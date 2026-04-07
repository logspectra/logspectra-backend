package com.loganalyzer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.loganalyzer.ai.preprocessing.LogChunker;
import com.loganalyzer.ai.preprocessing.LogSummarizer;
import com.loganalyzer.ai.preprocessing.StackTraceNormalizer;
import com.loganalyzer.ai.prompt.PromptBuilder;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import com.loganalyzer.infrastructure.ratelimit.AIRateLimiter;
import com.loganalyzer.service.CacheService;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OllamaAnalysisService extends AbstractChunkedAIAnalysisProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3";
    public OllamaAnalysisService(PromptBuilder promptBuilder,
                                 LogChunker chunker,
                                 LogSummarizer summarizer,
                                 StackTraceNormalizer stackTraceNormalizer,
                                 CacheService cacheService,
                                 AIRateLimiter rateLimiter,
                                 AnalysisResultParser responseParser) {
        super(promptBuilder, chunker, summarizer, stackTraceNormalizer, cacheService, rateLimiter, responseParser);
    }

    @Override
    public AIProvider provider() {
        return AIProvider.OLLAMA;
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
        String runtimeModel = StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : model;
        String runtimeBaseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_BASE_URL;
        String runtimeApiKey = request.resolvedApiKey();
        RestClient restClient = RestClient.builder().baseUrl(runtimeBaseUrl).build();

        Map<String, Object> payload = Map.of(
            "model", runtimeModel,
            "prompt", userPrompt,
            "stream", false,
            "format", "json"
        );

        RestClient.RequestBodySpec requestSpec = restClient.post()
            .uri("/api/generate")
            .contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(runtimeApiKey)) {
            requestSpec.header("Authorization", "Bearer " + runtimeApiKey);
        }

        JsonNode response = requestSpec
            .body(payload)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) {
            throw new AIAnalysisException("Ollama returned an empty response");
        }
        String content = response.path("response").asText("");
        if (!StringUtils.hasText(content)) {
            throw new AIAnalysisException("Ollama did not return response content");
        }
        return content;
    }

    @Override
    protected String callProvider(String model,
                                  String userPrompt,
                                  AIAnalysisRequest request,
                                  AIStreamListener streamListener) {
        if (streamListener == null || !streamListener.isEnabled()) {
            return callProvider(model, userPrompt, request);
        }

        String runtimeModel = StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : model;
        String runtimeBaseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_BASE_URL;
        String runtimeApiKey = request.resolvedApiKey();
        WebClient webClient = WebClient.builder().baseUrl(runtimeBaseUrl).build();

        Map<String, Object> payload = Map.of(
            "model", runtimeModel,
            "prompt", userPrompt,
            "stream", true,
            "format", "json"
        );

        try {
            StringBuilder assembled = new StringBuilder();
            WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(runtimeApiKey)) {
                requestSpec.header("Authorization", "Bearer " + runtimeApiKey);
            }

            requestSpec
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> new AIAnalysisException("Ollama stream request failed with status "
                        + response.statusCode().value()
                        + (StringUtils.hasText(body) ? ": " + body : ""))))
                .bodyToFlux(JsonNode.class)
                .doOnNext(node -> consumeChunkNode(node, assembled, streamListener))
                .blockLast(Duration.ofMinutes(3));

            String content = assembled.toString();
            if (!StringUtils.hasText(content)) {
                throw new AIAnalysisException("Ollama did not return response content");
            }
            return content;
        } catch (AIAnalysisException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AIAnalysisException("Failed to stream response from Ollama: " + ex.getMessage(), ex);
        }
    }

    private void consumeChunkNode(JsonNode node, StringBuilder assembled, AIStreamListener streamListener) {
        if (node == null) {
            return;
        }
        if (node.hasNonNull("error")) {
            throw new AIAnalysisException("Ollama stream error: " + node.get("error").asText());
        }
        String token = node.path("response").asText("");
        if (StringUtils.hasText(token)) {
            assembled.append(token);
            streamListener.onToken(token);
        }
    }
}
