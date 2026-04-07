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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class OpenAIAnalysisService extends AbstractChunkedAIAnalysisProvider {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_ANALYSIS_MODEL = "gpt-4-turbo-preview";
    private static final String DEFAULT_SUMMARY_MODEL = "gpt-3.5-turbo";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

    private final PromptBuilder promptBuilder;

    public OpenAIAnalysisService(PromptBuilder promptBuilder,
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
        return AIProvider.OPENAI;
    }

    @Override
    protected String getAnalysisModel() {
        return DEFAULT_ANALYSIS_MODEL;
    }

    @Override
    protected String getSummaryModel() {
        return DEFAULT_SUMMARY_MODEL;
    }

    @Override
    protected String callProvider(String model, String userPrompt, AIAnalysisRequest request) {
        String runtimeBaseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_BASE_URL;
        String runtimeModel = StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : model;
        String apiKey = request.resolvedApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new AIAnalysisException("OPENAI requires apiKey");
        }

        RestClient restClient = RestClient.builder().baseUrl(runtimeBaseUrl).build();
        Map<String, Object> payload = Map.of(
            "model", runtimeModel,
            "messages", List.of(
                Map.of("role", "system", "content", promptBuilder.getSystemPrompt()),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.1,
            "max_tokens", 1500,
            "response_format", Map.of("type", "json_object")
        );

        JsonNode body = restClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .body(payload)
            .retrieve()
            .body(JsonNode.class);

        if (body == null) {
            throw new AIAnalysisException("OpenAI returned an empty response");
        }

        JsonNode content = body.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || !StringUtils.hasText(content.asText())) {
            throw new AIAnalysisException("OpenAI did not return message content");
        }
        return content.asText();
    }

    @Override
    protected String callProvider(String model, String userPrompt, AIAnalysisRequest request, AIStreamListener streamListener) {
        if (streamListener == null || !streamListener.isEnabled()) {
            return callProvider(model, userPrompt, request);
        }

        String runtimeBaseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_BASE_URL;
        String runtimeModel = StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : model;
        String apiKey = request.resolvedApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new AIAnalysisException("OPENAI requires apiKey");
        }

        Map<String, Object> payload = Map.of(
            "model", runtimeModel,
            "messages", List.of(
                Map.of("role", "system", "content", promptBuilder.getSystemPrompt()),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.1,
            "stream", true
        );

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(runtimeBaseUrl + "/v1/chat/completions"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

            HttpResponse<java.util.stream.Stream<String>> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                throw new AIAnalysisException("OpenAI stream request failed with status " + response.statusCode());
            }

            StringBuilder assembled = new StringBuilder();
            try (java.util.stream.Stream<String> lines = response.body()) {
                lines.forEach(line -> consumeStreamLine(line, assembled, streamListener));
            }

            String content = assembled.toString();
            if (!StringUtils.hasText(content)) {
                throw new AIAnalysisException("OpenAI did not return message content");
            }
            return content;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AIAnalysisException("Failed to stream response from OpenAI: " + ex.getMessage(), ex);
        }
    }

    private void consumeStreamLine(String line, StringBuilder assembled, AIStreamListener streamListener) {
        if (!StringUtils.hasText(line)) {
            return;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return;
        }

        String data = trimmed.substring(5).trim();
        if ("[DONE]".equals(data)) {
            return;
        }

        try {
            JsonNode node = OBJECT_MAPPER.readTree(data);
            JsonNode delta = node.path("choices").path(0).path("delta");
            String token = delta.path("content").asText("");
            if (StringUtils.hasText(token)) {
                assembled.append(token);
                streamListener.onToken(token);
            }
        } catch (IOException ex) {
            throw new AIAnalysisException("Failed to parse OpenAI stream chunk", ex);
        }
    }
}
