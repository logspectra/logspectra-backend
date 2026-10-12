package com.loganalyzer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.loganalyzer.ai.preprocessing.LogChunker;
import com.loganalyzer.ai.preprocessing.LogSummarizer;
import com.loganalyzer.ai.preprocessing.StackTraceNormalizer;
import com.loganalyzer.ai.prompt.PromptBuilder;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import com.loganalyzer.infrastructure.ratelimit.AIRateLimiter;
import com.loganalyzer.service.CacheService;
import com.logspectra.ai.LogSpectraAiFacade;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class GeminiAnalysisService extends AbstractChunkedAIAnalysisProvider {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    private final PromptBuilder promptBuilder;

    @Value("${ai.gemini.api-key:${GEMINI_API_KEY:${GOOGLE_API_KEY:}}}")
    private String defaultApiKey;

    public GeminiAnalysisService(PromptBuilder promptBuilder,
                               LogChunker chunker,
                               LogSummarizer summarizer,
                               StackTraceNormalizer stackTraceNormalizer,
                               CacheService cacheService,
                               AIRateLimiter rateLimiter,
                               AnalysisResultParser responseParser,
                               LogSpectraAiFacade logSpectraAiFacade,
                               AiLogEventMapper aiLogEventMapper) {
        super(promptBuilder, chunker, summarizer, stackTraceNormalizer, cacheService, rateLimiter, responseParser,
            logSpectraAiFacade, aiLogEventMapper);
        this.promptBuilder = promptBuilder;
    }

    @Override
    public AIProvider provider() {
        return AIProvider.GEMINI;
    }

    @Override
    protected String getAnalysisModel() {
        return DEFAULT_MODEL;
    }

    @Override
    protected String getSummaryModel() {
        return DEFAULT_MODEL;
    }

    @Override
    protected String callProvider(String model, String userPrompt, AIAnalysisRequest request) {
        return generateContent(model, userPrompt, request, null);
    }

    @Override
    protected String callProvider(String model,
                                  String userPrompt,
                                  AIAnalysisRequest request,
                                  AIStreamListener streamListener) {
        return generateContent(model, userPrompt, request, streamListener);
    }

    private String generateContent(String model,
                                   String userPrompt,
                                   AIAnalysisRequest request,
                                   AIStreamListener streamListener) {
        String runtimeModel = normalizeModelName(StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : model);
        String runtimeBaseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_BASE_URL;
        String runtimeApiKey = StringUtils.hasText(request.resolvedApiKey()) ? request.resolvedApiKey() : defaultApiKey;

        if (!StringUtils.hasText(runtimeApiKey)) {
            throw new AIAnalysisException("GEMINI requires apiKey");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of("parts", List.of(Map.of("text", promptBuilder.getSystemPrompt()))));
        payload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))));
        payload.put("generationConfig", Map.of(
            "temperature", 0.1,
            "responseMimeType", "application/json"
        ));

        RestClient client = RestClient.builder().baseUrl(runtimeBaseUrl).build();
        JsonNode response = client.post()
            .uri("/v1beta/models/{model}:generateContent", runtimeModel)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-goog-api-key", runtimeApiKey)
            .body(payload)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) {
            throw new AIAnalysisException("Gemini returned an empty response");
        }

        String text = extractText(response);
        if (!StringUtils.hasText(text)) {
            throw new AIAnalysisException("Gemini did not return message content");
        }

        if (streamListener != null && streamListener.isEnabled()) {
            streamListener.onToken(text);
        }
        return text;
    }

    static String normalizeModelName(String model) {
        if (!StringUtils.hasText(model)) {
            return DEFAULT_MODEL;
        }
        String trimmed = model.trim();
        if (trimmed.startsWith("models/")) {
            return trimmed.substring("models/".length());
        }
        return trimmed;
    }

    static String extractText(JsonNode response) {
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder assembled = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (StringUtils.hasText(text)) {
                assembled.append(text);
            }
        }
        return assembled.toString();
    }
}
