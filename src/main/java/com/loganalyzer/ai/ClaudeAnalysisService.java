package com.loganalyzer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.ai.preprocessing.LogChunker;
import com.loganalyzer.ai.preprocessing.LogSummarizer;
import com.loganalyzer.ai.preprocessing.StackTraceNormalizer;
import com.loganalyzer.ai.prompt.PromptBuilder;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import com.loganalyzer.infrastructure.ratelimit.AIRateLimiter;
import com.loganalyzer.service.CacheService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class ClaudeAnalysisService extends AbstractChunkedAIAnalysisProvider {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-latest";
    private static final String DEFAULT_API_VERSION = "2023-06-01";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

    private final PromptBuilder promptBuilder;

    public ClaudeAnalysisService(PromptBuilder promptBuilder,
                                 LogChunker chunker,
                                 LogSummarizer summarizer,
                                 StackTraceNormalizer stackTraceNormalizer,
                                 CacheService cacheService,
                                 AIRateLimiter rateLimiter,
                                 AnalysisResultParser responseParser) {
        super(promptBuilder, chunker, summarizer, stackTraceNormalizer, cacheService, rateLimiter, responseParser);
        this.promptBuilder = promptBuilder;
    }

    @Override
    public AIProvider provider() {
        return AIProvider.CLAUDE;
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

        if (!StringUtils.hasText(runtimeApiKey)) {
            throw new AIAnalysisException("CLAUDE requires apiKey");
        }

        RestClient client = RestClient.builder().baseUrl(runtimeBaseUrl).build();

        Map<String, Object> payload = Map.of(
            "model", runtimeModel,
            "max_tokens", 1500,
            "temperature", 0.1,
            "system", promptBuilder.getSystemPrompt(),
            "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        JsonNode response = client.post()
            .uri("/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-api-key", runtimeApiKey)
            .header("anthropic-version", "2023-06-01")
            .body(payload)
            .retrieve()
            .body(JsonNode.class);

        if (response == null) {
            throw new AIAnalysisException("Claude returned an empty response");
        }

        JsonNode textNode = response.path("content").path(0).path("text");
        if (textNode.isMissingNode() || !StringUtils.hasText(textNode.asText())) {
            throw new AIAnalysisException("Claude did not return message content");
        }

        return textNode.asText();
    }

    @Override
    protected String callProvider(String model, String userPrompt, AIAnalysisRequest request, AIStreamListener streamListener) {
        if (streamListener == null || !streamListener.isEnabled()) {
            return callProvider(model, userPrompt, request);
        }

        String runtimeModel = StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : model;
        String runtimeBaseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_BASE_URL;
        String runtimeApiKey = request.resolvedApiKey();
        String runtimeApiVersion = StringUtils.hasText(request.setting("apiVersion")) ? request.setting("apiVersion") : DEFAULT_API_VERSION;

        if (!StringUtils.hasText(runtimeApiKey)) {
            throw new AIAnalysisException("CLAUDE requires apiKey");
        }

        Map<String, Object> payload = Map.of(
            "model", runtimeModel,
            "max_tokens", 1500,
            "temperature", 0.1,
            "stream", true,
            "system", promptBuilder.getSystemPrompt(),
            "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(runtimeBaseUrl + "/v1/messages"))
                .header("x-api-key", runtimeApiKey)
                .header("anthropic-version", runtimeApiVersion)
                .header("accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .header("content-type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

            HttpResponse<java.util.stream.Stream<String>> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                throw new AIAnalysisException("Claude stream request failed with status " + response.statusCode());
            }

            StringBuilder assembled = new StringBuilder();
            StreamFrame frame = new StreamFrame();
            try (java.util.stream.Stream<String> lines = response.body()) {
                lines.forEach(line -> consumeStreamLine(line, frame, assembled, streamListener));
            }

            String content = assembled.toString();
            if (!StringUtils.hasText(content)) {
                throw new AIAnalysisException("Claude did not return message content");
            }
            return content;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AIAnalysisException("Failed to stream response from Claude: " + ex.getMessage(), ex);
        }
    }

    private void consumeStreamLine(String line, StreamFrame frame, StringBuilder assembled, AIStreamListener streamListener) {
        if (!StringUtils.hasText(line)) {
            frame.flushIfReady(assembled, streamListener);
            return;
        }

        String trimmed = line.trim();
        if (trimmed.startsWith("event:")) {
            frame.event = trimmed.substring(6).trim();
            return;
        }
        if (!trimmed.startsWith("data:")) {
            return;
        }

        String data = trimmed.substring(5).trim();
        frame.data = data;
        frame.flushIfReady(assembled, streamListener);
    }

    private static final class StreamFrame {
        private String event;
        private String data;

        private void flushIfReady(StringBuilder assembled, AIStreamListener streamListener) {
            if (!StringUtils.hasText(event) || !StringUtils.hasText(data)) {
                return;
            }

            if ("error".equals(event)) {
                throw new AIAnalysisException("Claude stream error: " + data);
            }

            try {
                JsonNode node = OBJECT_MAPPER.readTree(data);
                if ("content_block_delta".equals(event)) {
                    String token = node.path("delta").path("text").asText("");
                    if (StringUtils.hasText(token)) {
                        assembled.append(token);
                        streamListener.onToken(token);
                    }
                }
            } catch (IOException ex) {
                throw new AIAnalysisException("Failed to parse Claude stream chunk", ex);
            } finally {
                event = null;
                data = null;
            }
        }
    }
}
