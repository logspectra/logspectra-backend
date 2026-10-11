package com.loganalyzer.service;

import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.api.dto.request.AISettingsRequest;
import com.loganalyzer.api.dto.response.AISettingsResponse;
import com.loganalyzer.api.dto.response.ProviderModelsResponse;
import com.loganalyzer.domain.model.AISettings;
import com.loganalyzer.domain.repository.AISettingsRepository;
import com.loganalyzer.infrastructure.exception.NoActiveAISettingsException;
import com.loganalyzer.infrastructure.exception.AIProviderModelsFetchException;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class AISettingsService {

    @Value("${ai.ollama.host:localhost}")
    private String ollamaHost;

    @Value("${ai.ollama.port:11434}")
    private int ollamaPort;

    @Value("${ai.openai.base-url:https://api.openai.com}")
    private String openAiBaseUrl;

    @Value("${ai.openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${ai.claude.base-url:https://api.anthropic.com}")
    private String claudeBaseUrl;

    @Value("${ai.claude.api-key:${CLAUDE_API_KEY:}}")
    private String claudeApiKey;

    @Value("${ai.claude.api-version:2023-06-01}")
    private String claudeApiVersion;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com}")
    private String geminiBaseUrl;

    @Value("${ai.gemini.api-key:${GEMINI_API_KEY:${GOOGLE_API_KEY:}}}")
    private String geminiApiKey;

    private final AISettingsRepository repository;
    private final CacheService cacheService;

    @Transactional
    public AISettingsResponse createOrUpdate(AISettingsRequest request) {
        validate(request);
        AISettings settings = repository.findByProvider(request.getProvider()).orElseGet(AISettings::new);
        mapRequest(settings, request);
        if (Boolean.TRUE.equals(request.getActive())) {
            deactivateAll();
            settings.setActive(true);
        } else if (settings.getId() == null) {
            settings.setActive(false);
        }

        AISettings saved = repository.save(settings);
        invalidateCache();
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AISettingsResponse getActive() {
        AISettings active = getActiveSettingsEntity();
        return toResponse(active);
    }

    @Transactional
    public AISettingsResponse update(UUID id, AISettingsRequest request) {
        validate(request);
        AISettings settings = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AI settings not found: " + id));

        mapRequest(settings, request);
        if (Boolean.TRUE.equals(request.getActive())) {
            deactivateAll();
            settings.setActive(true);
        }

        AISettings saved = repository.save(settings);
        invalidateCache();
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        AISettings settings = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AI settings not found: " + id));
        repository.delete(settings);
        invalidateCache();
    }

    @Transactional
    public AISettingsResponse activate(UUID id) {
        AISettings settings = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AI settings not found: " + id));
        deactivateAll();
        settings.setActive(true);
        AISettings saved = repository.save(settings);
        invalidateCache();
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AISettings getActiveSettingsEntity() {
        return cacheService.getActiveAISettings()
            .orElseGet(() -> {
                AISettings active = repository.findByIsActiveTrue()
                    .orElseThrow(() -> new NoActiveAISettingsException("No active AI provider settings configured"));
                cacheService.storeActiveAISettings(active, Duration.ofMinutes(5));
                return active;
            });
    }


    @Transactional(readOnly = true)
    public ProviderModelsResponse getModels(AIProvider provider) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        repository.findByProvider(provider)
            .map(AISettings::getModelName)
            .filter(StringUtils::hasText)
            .ifPresent(models::add);

        models.addAll(fetchRuntimeModels(provider));

        return ProviderModelsResponse.builder()
            .provider(provider)
            .models(new ArrayList<>(models))
            .build();
    }

    @Transactional(readOnly = true)
    public Optional<AISettings> findByProvider(AIProvider provider) {
        return repository.findByProvider(provider);
    }

    public Map<String, Object> toProviderSettings(AISettings settings) {
        return Map.of(
            "apiKey", settings.getApiKey() == null ? "" : settings.getApiKey(),
            "model", settings.getModelName(),
            "baseUrl", settings.getBaseUrl()
        );
    }

    private List<String> fetchRuntimeModels(AIProvider provider) {
        return switch (provider) {
            case OLLAMA -> fetchOllamaModelsFromApi();
            case OPENAI -> fetchOpenAiModelsFromApi();
            case CLAUDE -> fetchClaudeModelsFromApi();
            case GEMINI -> fetchGeminiModelsFromApi();
        };
    }

    private List<String> fetchOllamaModelsFromApi() {
        String baseUrl = repository.findByProvider(AIProvider.OLLAMA)
            .map(AISettings::getBaseUrl)
            .filter(StringUtils::hasText)
            .orElse(defaultOllamaBaseUrl());

        try {
            RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
            Map<?, ?> response = restClient.get()
                .uri("/api/tags")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

            if (response == null || !(response.get("models") instanceof List<?> models)) {
                return List.of();
            }

            return models.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(model -> model.get("name"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .toList();
        } catch (RuntimeException ex) {
            throw new AIProviderModelsFetchException(AIProvider.OLLAMA, "Failed to fetch Ollama models", ex);
        }
    }

    private List<String> fetchOpenAiModelsFromApi() {
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalArgumentException("OPENAI API key is required to fetch models");
        }

        String baseUrl = repository.findByProvider(AIProvider.OPENAI)
            .map(AISettings::getBaseUrl)
            .filter(StringUtils::hasText)
            .orElse(openAiBaseUrl);

        try {
            RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
            Map<?, ?> response = restClient.get()
                .uri("/v1/models")
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + openAiApiKey)
                .retrieve()
                .body(Map.class);

            if (response == null || !(response.get("data") instanceof List<?> models)) {
                return List.of();
            }

            return models.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(model -> model.get("id"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .toList();
        } catch (RuntimeException ex) {
            throw new AIProviderModelsFetchException(AIProvider.OPENAI, "Failed to fetch OpenAI models", ex);
        }
    }

    private List<String> fetchClaudeModelsFromApi() {
        if (!StringUtils.hasText(claudeApiKey)) {
            throw new IllegalArgumentException("CLAUDE API key is required to fetch models");
        }

        try {
            RestClient restClient = RestClient.builder().baseUrl(claudeBaseUrl).build();
            Map<?, ?> response = restClient.get()
                .uri("/v1/models")
                .accept(MediaType.APPLICATION_JSON)
                .header("x-api-key", claudeApiKey)
                .header("anthropic-version", claudeApiVersion)
                .retrieve()
                .body(Map.class);

            if (response == null || !(response.get("data") instanceof List<?> models)) {
                return List.of();
            }

            return models.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(model -> model.get("id"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .toList();
        } catch (RuntimeException ex) {
            throw new AIProviderModelsFetchException(AIProvider.CLAUDE, "Failed to fetch Anthropic models", ex);
        }
    }

    private void validate(AISettingsRequest request) {
        if ((request.getProvider() == AIProvider.OPENAI || request.getProvider() == AIProvider.CLAUDE)
            && !StringUtils.hasText(request.getApiKey())) {
            throw new IllegalArgumentException(request.getProvider() + " requires apiKey");
        }

        if (request.getProvider() == AIProvider.GEMINI
            && !StringUtils.hasText(request.getApiKey())
            && !StringUtils.hasText(geminiApiKey)) {
            throw new IllegalArgumentException("GEMINI requires apiKey");
        }

        if (request.getProvider() == AIProvider.OLLAMA && !StringUtils.hasText(request.getBaseUrl())) {
            throw new IllegalArgumentException("OLLAMA requires baseUrl");
        }
    }

    private List<String> fetchGeminiModelsFromApi() {
        String apiKey = repository.findByProvider(AIProvider.GEMINI)
            .map(AISettings::getApiKey)
            .filter(StringUtils::hasText)
            .orElse(geminiApiKey);

        if (!StringUtils.hasText(apiKey)) {
            return List.of("gemini-2.0-flash", "gemini-flash-latest");
        }

        String baseUrl = repository.findByProvider(AIProvider.GEMINI)
            .map(AISettings::getBaseUrl)
            .filter(StringUtils::hasText)
            .orElse(geminiBaseUrl);

        try {
            RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
            Map<?, ?> response = restClient.get()
                .uri("/v1beta/models")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-goog-api-key", apiKey)
                .retrieve()
                .body(Map.class);

            if (response == null || !(response.get("models") instanceof List<?> models)) {
                return List.of();
            }

            return models.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(model -> model.get("name"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .map(name -> name.startsWith("models/") ? name.substring("models/".length()) : name)
                .filter(name -> name.contains("gemini"))
                .toList();
        } catch (RuntimeException ex) {
            throw new AIProviderModelsFetchException(AIProvider.GEMINI, "Failed to fetch Gemini models", ex);
        }
    }

    private String defaultOllamaBaseUrl() {
        return "http://" + ollamaHost + ":" + ollamaPort;
    }

    private void mapRequest(AISettings settings, AISettingsRequest request) {
        settings.setProvider(request.getProvider());
        if (request.getProvider() == AIProvider.GEMINI
            && !StringUtils.hasText(request.getApiKey())
            && StringUtils.hasText(geminiApiKey)) {
            settings.setApiKey(geminiApiKey);
        } else {
            settings.setApiKey(request.getApiKey());
        }
        settings.setModelName(request.getModelName());
        if (request.getProvider() == AIProvider.GEMINI && !StringUtils.hasText(request.getBaseUrl())) {
            settings.setBaseUrl(geminiBaseUrl);
        } else {
            settings.setBaseUrl(request.getBaseUrl());
        }
        if (request.getActive() != null) {
            settings.setActive(request.getActive());
        }
    }

    private void deactivateAll() {
        repository.findByIsActiveTrue().ifPresent(active -> {
            active.setActive(false);
            repository.save(active);
        });
    }

    private void invalidateCache() {
        cacheService.evictActiveAISettings();
    }

    private AISettingsResponse toResponse(AISettings settings) {
        return AISettingsResponse.builder()
            .id(settings.getId())
            .provider(settings.getProvider())
            .apiKey(mask(settings.getApiKey()))
            .modelName(settings.getModelName())
            .baseUrl(settings.getBaseUrl())
            .active(settings.isActive())
            .createdAt(settings.getCreatedAt())
            .updatedAt(settings.getUpdatedAt())
            .build();
    }

    private String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }
}
