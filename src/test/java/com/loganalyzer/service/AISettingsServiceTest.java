package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.api.dto.request.AISettingsRequest;
import com.loganalyzer.api.dto.response.AISettingsResponse;
import com.loganalyzer.domain.model.AISettings;
import com.loganalyzer.domain.repository.AISettingsRepository;
import com.loganalyzer.infrastructure.exception.NoActiveAISettingsException;
import com.loganalyzer.infrastructure.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AISettingsServiceTest {

    @Mock
    private AISettingsRepository aiSettingsRepository;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private AISettingsService aiSettingsService;

    private UUID settingsId;
    private AISettings aiSettings;

    @BeforeEach
    void setUp() {
        settingsId = UUID.randomUUID();
        aiSettings = AISettings.builder()
            .id(settingsId)
            .provider(AIProvider.OLLAMA)
            .apiKey("test-key")
            .modelName("llama2")
            .baseUrl("http://localhost:11434")
            .isActive(true)
            .build();
    }

    @Test
    void createOrUpdateShouldPersistSettings() {
        AISettingsRequest request = new AISettingsRequest();
        request.setProvider(AIProvider.OLLAMA);
        request.setApiKey("test-key");
        request.setModelName("llama2");
        request.setBaseUrl("http://localhost:11434");

        when(aiSettingsRepository.findByProvider(AIProvider.OLLAMA)).thenReturn(Optional.empty());
        when(aiSettingsRepository.save(any(AISettings.class))).thenReturn(aiSettings);

        AISettingsResponse response = aiSettingsService.createOrUpdate(request);

        assertNotNull(response);
        assertEquals(AIProvider.OLLAMA, response.getProvider());
        assertEquals("llama2", response.getModelName());
        verify(aiSettingsRepository).save(any(AISettings.class));
        verify(cacheService).evictActiveAISettings();
    }

    @Test
    void getActiveShouldReturnActiveSettings() {
        when(cacheService.getActiveAISettings()).thenReturn(Optional.of(aiSettings));

        AISettingsResponse response = aiSettingsService.getActive();

        assertNotNull(response);
        assertEquals(AIProvider.OLLAMA, response.getProvider());
        assertTrue(response.isActive());
    }

    @Test
    void getActiveShouldThrowWhenNoActiveSettings() {
        when(cacheService.getActiveAISettings()).thenReturn(Optional.empty());
        when(aiSettingsRepository.findByIsActiveTrue()).thenReturn(Optional.empty());

        assertThrows(NoActiveAISettingsException.class, () -> aiSettingsService.getActive());
    }

    @Test
    void updateShouldModifyExistingSettings() {
        AISettingsRequest request = new AISettingsRequest();
        request.setProvider(AIProvider.OPENAI);
        request.setApiKey("new-key");
        request.setModelName("gpt-4");
        request.setBaseUrl("https://api.openai.com");

        AISettings updatedSettings = AISettings.builder()
            .id(settingsId)
            .provider(AIProvider.OPENAI)
            .apiKey("new-key")
            .modelName("gpt-4")
            .baseUrl("https://api.openai.com")
            .isActive(true)
            .build();

        when(aiSettingsRepository.findById(settingsId)).thenReturn(Optional.of(aiSettings));
        when(aiSettingsRepository.save(any(AISettings.class))).thenReturn(updatedSettings);

        AISettingsResponse response = aiSettingsService.update(settingsId, request);

        assertNotNull(response);
        assertEquals(AIProvider.OPENAI, response.getProvider());
        assertEquals("gpt-4", response.getModelName());
        verify(cacheService).evictActiveAISettings();
    }

    @Test
    void updateShouldThrowWhenSettingsNotFound() {
        AISettingsRequest request = new AISettingsRequest();
        request.setProvider(AIProvider.OPENAI);
        request.setApiKey("new-key");
        request.setModelName("gpt-4");
        request.setBaseUrl("https://api.openai.com");

        when(aiSettingsRepository.findById(settingsId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
            aiSettingsService.update(settingsId, request)
        );
    }

    @Test
    void deleteShouldRemoveSettings() {
        when(aiSettingsRepository.findById(settingsId)).thenReturn(Optional.of(aiSettings));

        aiSettingsService.delete(settingsId);

        verify(aiSettingsRepository).delete(aiSettings);
        verify(cacheService).evictActiveAISettings();
    }

    @Test
    void deleteShouldThrowWhenSettingsNotFound() {
        when(aiSettingsRepository.findById(settingsId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> aiSettingsService.delete(settingsId));
    }

    @Test
    void activateShouldSetSettingsActive() {
        AISettings inactiveSettings = AISettings.builder()
            .id(settingsId)
            .provider(AIProvider.OLLAMA)
            .apiKey("test-key")
            .modelName("llama2")
            .baseUrl("http://localhost:11434")
            .isActive(false)
            .build();

        AISettings activeSettings = AISettings.builder()
            .id(settingsId)
            .provider(AIProvider.OLLAMA)
            .apiKey("test-key")
            .modelName("llama2")
            .baseUrl("http://localhost:11434")
            .isActive(true)
            .build();

        when(aiSettingsRepository.findById(settingsId)).thenReturn(Optional.of(inactiveSettings));
        when(aiSettingsRepository.findByIsActiveTrue()).thenReturn(Optional.of(aiSettings));
        when(aiSettingsRepository.save(any(AISettings.class))).thenReturn(activeSettings);

        AISettingsResponse response = aiSettingsService.activate(settingsId);

        assertNotNull(response);
        assertTrue(response.isActive());
        verify(aiSettingsRepository, atLeast(2)).save(any(AISettings.class));
    }

    @Test
    void findByProviderShouldReturnSettingsForProvider() {
        when(aiSettingsRepository.findByProvider(AIProvider.OLLAMA))
            .thenReturn(Optional.of(aiSettings));

        Optional<AISettings> result = aiSettingsService.findByProvider(AIProvider.OLLAMA);

        assertTrue(result.isPresent());
        assertEquals(AIProvider.OLLAMA, result.get().getProvider());
    }
}

