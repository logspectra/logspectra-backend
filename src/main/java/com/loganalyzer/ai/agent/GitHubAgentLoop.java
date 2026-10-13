package com.loganalyzer.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.github.GitHubAppProperties;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Provider-agnostic GitHub agent: list_repo_files → get_file_content → JSON analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubAgentLoop {

    private static final String CLAUDE_API_VERSION = "2023-06-01";

    private final GitHubAgentToolSupport support;
    private final GitHubAppProperties properties;
    private final ObjectMapper objectMapper;

    public String run(AIProvider provider,
                      String model,
                      String userPrompt,
                      AgentSession session,
                      String apiBaseUrl,
                      String apiKey) {
        int maxIterations = Math.max(1, properties.getAgent().getMaxIterations());
        long started = System.currentTimeMillis();
        return switch (provider) {
            case OPENAI -> runOpenAi(model, userPrompt, session, apiBaseUrl, apiKey, maxIterations, started);
            case GEMINI -> runGemini(model, userPrompt, session, apiBaseUrl, apiKey, maxIterations, started);
            case CLAUDE -> runClaude(model, userPrompt, session, apiBaseUrl, apiKey, maxIterations, started);
            case OLLAMA -> runOllama(model, userPrompt, session, apiBaseUrl, apiKey, maxIterations, started);
        };
    }

    private String runOpenAi(String model,
                           String userPrompt,
                           AgentSession session,
                           String apiBaseUrl,
                           String apiKey,
                           int maxIterations,
                           long started) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", support.agentSystemPrompt()));
        messages.add(Map.of("role", "user", "content", support.agentUserPrompt(userPrompt, session)));

        RestClient client = RestClient.builder().baseUrl(apiBaseUrl).build();

        for (int i = 0; i < maxIterations; i++) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", 0.1);
            payload.put("max_tokens", 4096);
            payload.set("messages", objectMapper.valueToTree(messages));
            payload.set("tools", objectMapper.valueToTree(support.registry().openaiToolDefinitions()));
            payload.put("tool_choice", "auto");

            JsonNode body = postJson(client, "/v1/chat/completions", payload, apiKey, null);
            JsonNode message = body.path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                messages.add(objectMapper.convertValue(message, Map.class));
                for (JsonNode call : toolCalls) {
                    String toolCallId = call.path("id").asText();
                    String toolName = call.path("function").path("name").asText();
                    String argsJson = call.path("function").path("arguments").asText("{}");
                    messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCallId,
                        "content", support.executeTool(session, toolName, argsJson)
                    ));
                }
                continue;
            }
            String content = message.path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new AIAnalysisException("OpenAI agent finished without content");
            }
            logAgentExit(session, "OPENAI", i + 1, started);
            return content;
        }
        return openAiForcedFinal(client, model, messages, apiKey, session, maxIterations, started);
    }

    private String openAiForcedFinal(RestClient client,
                                     String model,
                                     List<Map<String, Object>> messages,
                                     String apiKey,
                                     AgentSession session,
                                     int maxIterations,
                                     long started) {
        messages.add(Map.of(
            "role", "user",
            "content", "Stop calling tools. Return the required analysis JSON now based on files you already fetched."
        ));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("temperature", 0.1);
        payload.put("max_tokens", 4096);
        payload.set("messages", objectMapper.valueToTree(messages));
        payload.set("response_format", objectMapper.valueToTree(Map.of("type", "json_object")));
        JsonNode finalBody = postJson(client, "/v1/chat/completions", payload, apiKey, null);
        String content = finalBody.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new AIAnalysisException("OpenAI agent exceeded maxIterations without a final answer");
        }
        log.warn("operation=agentLoop feature=github-agent status=exit forcedFinal=true provider=OPENAI service={} iterations={} durationMs={}",
            session.getServiceName(), maxIterations, System.currentTimeMillis() - started);
        return content;
    }

    private String runGemini(String model,
                             String userPrompt,
                             AgentSession session,
                             String apiBaseUrl,
                             String apiKey,
                             int maxIterations,
                             long started) {
        String normalizedModel = GeminiAnalysisServiceHelper.normalizeModelName(model);
        ArrayNode contents = objectMapper.createArrayNode();
        contents.add(objectMapper.createObjectNode()
            .put("role", "user")
            .set("parts", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("text", support.agentUserPrompt(userPrompt, session)))));

        RestClient client = RestClient.builder().baseUrl(apiBaseUrl).build();

        for (int i = 0; i < maxIterations; i++) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("systemInstruction", objectMapper.createObjectNode()
                .set("parts", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                    .put("text", support.agentSystemPrompt()))));
            payload.set("contents", contents);
            payload.set("tools", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .set("functionDeclarations", objectMapper.valueToTree(support.registry().geminiFunctionDeclarations()))));
            ObjectNode genConfig = objectMapper.createObjectNode();
            genConfig.put("temperature", 0.1);
            payload.set("generationConfig", genConfig);

            JsonNode body = client.post()
                .uri("/v1beta/models/{model}:generateContent", normalizedModel)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-goog-api-key", apiKey)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

            if (body == null) {
                throw new AIAnalysisException("Gemini agent returned empty response");
            }

            JsonNode parts = body.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray()) {
                throw new AIAnalysisException("Gemini agent returned no content parts");
            }

            ObjectNode modelContent = objectMapper.createObjectNode().put("role", "model");
            ArrayNode modelParts = objectMapper.createArrayNode();
            ArrayNode toolResponseParts = objectMapper.createArrayNode();
            boolean hadToolCall = false;
            String textAnswer = null;

            for (JsonNode part : parts) {
                if (part.has("functionCall")) {
                    hadToolCall = true;
                    JsonNode fc = part.get("functionCall");
                    String toolName = fc.path("name").asText();
                    String argsJson = fc.has("args") ? fc.get("args").toString() : "{}";
                    modelParts.add(part);
                    String result = support.executeTool(session, toolName, argsJson);
                    toolResponseParts.add(objectMapper.createObjectNode()
                        .set("functionResponse", objectMapper.createObjectNode()
                            .put("name", toolName)
                            .set("response", objectMapper.createObjectNode().put("result", result))));
                } else if (part.has("text")) {
                    textAnswer = part.path("text").asText("");
                    modelParts.add(part);
                }
            }

            if (hadToolCall) {
                modelContent.set("parts", modelParts);
                contents.add(modelContent);
                contents.add(objectMapper.createObjectNode()
                    .put("role", "user")
                    .set("parts", toolResponseParts));
                continue;
            }

            if (StringUtils.hasText(textAnswer)) {
                logAgentExit(session, "GEMINI", i + 1, started);
                return textAnswer;
            }
            throw new AIAnalysisException("Gemini agent finished without text or tool calls");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("systemInstruction", objectMapper.createObjectNode()
            .set("parts", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("text", support.agentSystemPrompt()))));
        contents.add(objectMapper.createObjectNode()
            .put("role", "user")
            .set("parts", objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("text", "Stop calling tools. Return the required analysis JSON now."))));
        payload.set("contents", contents);
        payload.set("generationConfig", objectMapper.createObjectNode()
            .put("temperature", 0.1)
            .put("responseMimeType", "application/json"));

        JsonNode finalBody = client.post()
            .uri("/v1beta/models/{model}:generateContent", normalizedModel)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-goog-api-key", apiKey)
            .body(payload)
            .retrieve()
            .body(JsonNode.class);
        String text = GeminiAnalysisServiceHelper.extractText(finalBody);
        if (!StringUtils.hasText(text)) {
            throw new AIAnalysisException("Gemini agent exceeded maxIterations without a final answer");
        }
        return text;
    }

    private String runClaude(String model,
                             String userPrompt,
                             AgentSession session,
                             String apiBaseUrl,
                             String apiKey,
                             int maxIterations,
                             long started) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", support.agentUserPrompt(userPrompt, session)));

        RestClient client = RestClient.builder().baseUrl(apiBaseUrl).build();

        for (int i = 0; i < maxIterations; i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("max_tokens", 4096);
            payload.put("temperature", 0.1);
            payload.put("system", support.agentSystemPrompt());
            payload.put("tools", support.registry().claudeToolDefinitions());
            payload.put("messages", messages);

            JsonNode body = postJson(client, "/v1/messages", payload, apiKey, CLAUDE_API_VERSION);
            String stopReason = body.path("stop_reason").asText("");
            JsonNode contentBlocks = body.path("content");
            if (!contentBlocks.isArray()) {
                throw new AIAnalysisException("Claude agent returned no content");
            }

            List<Map<String, Object>> assistantContent = new ArrayList<>();
            List<Map<String, Object>> toolResults = new ArrayList<>();
            String textAnswer = null;

            for (JsonNode block : contentBlocks) {
                String type = block.path("type").asText("");
                if ("tool_use".equals(type)) {
                    String id = block.path("id").asText();
                    String toolName = block.path("name").asText();
                    JsonNode input = block.path("input");
                    String argsJson = input.isObject() ? input.toString() : "{}";
                    assistantContent.add(Map.of(
                        "type", "tool_use",
                        "id", id,
                        "name", toolName,
                        "input", objectMapper.convertValue(input, Map.class)
                    ));
                    toolResults.add(Map.of(
                        "type", "tool_result",
                        "tool_use_id", id,
                        "content", support.executeTool(session, toolName, argsJson)
                    ));
                } else if ("text".equals(type)) {
                    textAnswer = block.path("text").asText("");
                    assistantContent.add(Map.of("type", "text", "text", textAnswer));
                }
            }

            if ("tool_use".equals(stopReason) || !toolResults.isEmpty()) {
                messages.add(Map.of("role", "assistant", "content", assistantContent));
                messages.add(Map.of("role", "user", "content", toolResults));
                continue;
            }

            if (StringUtils.hasText(textAnswer)) {
                logAgentExit(session, "CLAUDE", i + 1, started);
                return textAnswer;
            }
            throw new AIAnalysisException("Claude agent finished without text");
        }

        messages.add(Map.of("role", "user", "content", "Stop calling tools. Return the required analysis JSON now."));
        Map<String, Object> payload = Map.of(
            "model", model,
            "max_tokens", 4096,
            "temperature", 0.1,
            "system", support.agentSystemPrompt(),
            "messages", messages
        );
        JsonNode finalBody = postJson(client, "/v1/messages", payload, apiKey, CLAUDE_API_VERSION);
        String text = extractClaudeText(finalBody);
        if (!StringUtils.hasText(text)) {
            throw new AIAnalysisException("Claude agent exceeded maxIterations without a final answer");
        }
        return text;
    }

    private String runOllama(String model,
                             String userPrompt,
                             AgentSession session,
                             String apiBaseUrl,
                             String apiKey,
                             int maxIterations,
                             long started) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", support.agentSystemPrompt()));
        messages.add(Map.of("role", "user", "content", support.agentUserPrompt(userPrompt, session)));

        RestClient client = RestClient.builder().baseUrl(apiBaseUrl).build();

        for (int i = 0; i < maxIterations; i++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("stream", false);
            payload.put("tools", support.registry().openaiToolDefinitions());

            RestClient.RequestBodySpec spec = client.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(apiKey)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            JsonNode body = spec.body(payload).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new AIAnalysisException("Ollama agent returned empty response");
            }

            JsonNode message = body.path("message");
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                messages.add(objectMapper.convertValue(message, Map.class));
                for (JsonNode call : toolCalls) {
                    String toolName = call.path("function").path("name").asText();
                    String argsJson = call.path("function").path("arguments").asText("{}");
                    messages.add(Map.of(
                        "role", "tool",
                        "content", support.executeTool(session, toolName, argsJson)
                    ));
                }
                continue;
            }

            String content = message.path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new AIAnalysisException("Ollama agent finished without content (model may not support tools)");
            }
            logAgentExit(session, "OLLAMA", i + 1, started);
            return content;
        }

        Map<String, Object> payload = Map.of(
            "model", model,
            "messages", messages,
            "stream", false,
            "format", "json"
        );
        RestClient.RequestBodySpec spec = client.post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(apiKey)) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        JsonNode finalBody = spec.body(payload).retrieve().body(JsonNode.class);
        String content = finalBody == null ? "" : finalBody.path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new AIAnalysisException("Ollama agent exceeded maxIterations without a final answer");
        }
        return content;
    }

    private JsonNode postJson(RestClient client, String path, Object payload, String apiKey, String anthropicVersion) {
        RestClient.RequestBodySpec spec = client.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(apiKey)) {
            if (path.contains("/messages")) {
                spec = spec.header("x-api-key", apiKey);
            } else {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
        }
        if (StringUtils.hasText(anthropicVersion)) {
            spec = spec.header("anthropic-version", anthropicVersion);
        }
        JsonNode body = spec.body(payload).retrieve().body(JsonNode.class);
        if (body == null) {
            throw new AIAnalysisException("Agent provider returned empty response for " + path);
        }
        return body;
    }

    private static String extractClaudeText(JsonNode body) {
        JsonNode content = body.path("content");
        if (!content.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText(""));
            }
        }
        return sb.toString();
    }

    private void logAgentExit(AgentSession session, String provider, int iterations, long started) {
        log.info("operation=agentLoop feature=github-agent status=exit provider={} service={} iterations={} fetched={} durationMs={}",
            provider, session.getServiceName(), iterations, session.getFetchedPaths().size(), System.currentTimeMillis() - started);
    }

    /** Avoid coupling agent package to {@link com.loganalyzer.ai.GeminiAnalysisService}. */
    static final class GeminiAnalysisServiceHelper {
        private GeminiAnalysisServiceHelper() {}

        static String normalizeModelName(String model) {
            if (!StringUtils.hasText(model)) {
                return "gemini-2.0-flash";
            }
            String trimmed = model.trim();
            if (trimmed.startsWith("models/")) {
                return trimmed.substring("models/".length());
            }
            return trimmed;
        }

        static String extractText(JsonNode response) {
            if (response == null) {
                return "";
            }
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
}
