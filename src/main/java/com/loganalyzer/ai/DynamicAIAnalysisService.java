package com.loganalyzer.ai;

import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DynamicAIAnalysisService {

    private final AIProvider defaultProvider;
    private final AIProviderRegistry registry;

    public DynamicAIAnalysisService(@Value("${ai.default-provider:${ai.provider:OPENAI}}") String defaultProvider,
                                    AIProviderRegistry registry) {
        this.defaultProvider = AIProvider.from(defaultProvider);
        this.registry = registry;
    }

    public AIAnalysisExecution analyze(List<LogDocument> logs, AIAnalysisRequest request) {
        return analyze(logs, request, AIStreamListener.NO_OP);
    }

    public AIAnalysisExecution analyze(List<LogDocument> logs, AIAnalysisRequest request, AIStreamListener streamListener) {
        AIProvider requestedProvider = request.getProvider() != null ? request.getProvider() : defaultProvider;
        AIProvider fallbackProvider = request.getFallbackProvider() != null ? request.getFallbackProvider() : defaultProvider;
        try {
            return executeProvider(logs, request, requestedProvider, streamListener);
        } catch (Exception ex) {
            if (requestedProvider == fallbackProvider) {
                throw toAnalysisException(requestedProvider, ex);
            }
            log.warn("AI provider {} failed, falling back to default provider {}: {}",
                requestedProvider, fallbackProvider, ex.getMessage(), ex);
            try {
                AIAnalysisExecution fallbackExecution = executeProvider(logs,
                    AIAnalysisRequest.builder()
                        .provider(fallbackProvider)
                        .apiKey(request.getApiKey())
                        .model(request.getModel())
                        .baseUrl(request.getBaseUrl())
                        .openAiApiKey(request.getOpenAiApiKey())
                        .providerSettings(request.getProviderSettings())
                        .build(),
                    fallbackProvider,
                    streamListener);
                log.info("Fallback to provider {} succeeded", fallbackProvider);
                return fallbackExecution;
            } catch (Exception fallbackEx) {
                throw toAnalysisException(fallbackProvider, fallbackEx);
            }
        }
    }

    private AIAnalysisExecution executeProvider(List<LogDocument> logs,
                                                AIAnalysisRequest request,
                                                AIProvider provider,
                                                AIStreamListener streamListener) {
        AIAnalysisProvider strategy = registry.get(provider);
        Instant started = Instant.now();
        try {
            AnalysisResult result = strategy.analyze(logs, request, streamListener);
            long durationMs = Duration.between(started, Instant.now()).toMillis();
            log.info("AI analysis completed. provider={}, executionMs={}", provider, durationMs);
            return AIAnalysisExecution.builder().result(result).providerUsed(provider).build();
        } catch (Exception ex) {
            long durationMs = Duration.between(started, Instant.now()).toMillis();
            log.error("AI analysis failed. provider={}, executionMs={}, error={}", provider, durationMs, ex.getMessage(), ex);
            throw ex;
        }
    }

    private AIAnalysisException toAnalysisException(AIProvider provider, Exception ex) {
        if (ex instanceof AIAnalysisException aiAnalysisException) {
            return aiAnalysisException;
        }
        return new AIAnalysisException("Provider " + provider + " failed: " + ex.getMessage(), ex);
    }
}
