package com.loganalyzer.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loganalyzer.ai.AIAnalysisRequest;
import com.loganalyzer.ai.prompt.PromptBuilder;
import com.loganalyzer.github.GitHubAppProperties;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import java.util.ArrayList;
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
 * Iterative OpenAI tool-calling loop:
 * list_repo_files → get_file_content (repeat) → final JSON analysis with filePath/newContent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIGitHubAgentLoop {

    private final AgentToolRegistry toolRegistry;
    private final GitHubAppProperties properties;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public String run(String model,
                      String userPrompt,
                      AIAnalysisRequest request,
                      AgentSession session,
                      String apiBaseUrl,
                      String apiKey) {
        int maxIterations = Math.max(1, properties.getAgent().getMaxIterations());
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", agentSystemPrompt()));
        messages.add(Map.of("role", "user", "content", agentUserPrompt(userPrompt, session)));

        RestClient client = RestClient.builder().baseUrl(apiBaseUrl).build();
        long started = System.currentTimeMillis();

        for (int i = 0; i < maxIterations; i++) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", 0.1);
            payload.put("max_tokens", 4096);
            payload.set("messages", objectMapper.valueToTree(messages));
            payload.set("tools", objectMapper.valueToTree(toolRegistry.openaiToolDefinitions()));
            payload.put("tool_choice", "auto");

            JsonNode body = client.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

            if (body == null) {
                throw new AIAnalysisException("OpenAI agent returned empty response");
            }

            JsonNode message = body.path("choices").path(0).path("message");
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                messages.add(objectMapper.convertValue(message, Map.class));
                for (JsonNode call : toolCalls) {
                    String toolCallId = call.path("id").asText();
                    String toolName = call.path("function").path("name").asText();
                    String argsJson = call.path("function").path("arguments").asText("{}");
                    String result = executeTool(session, toolName, argsJson);
                    messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCallId,
                        "content", result
                    ));
                    log.debug("operation=agentTool feature=github-agent status=exit iteration={} tool={} resultChars={}",
                        i, toolName, result.length());
                }
                continue;
            }

            String content = message.path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new AIAnalysisException("OpenAI agent finished without content");
            }
            log.info("operation=agentLoop feature=github-agent status=exit service={} iterations={} fetched={} durationMs={}",
                session.getServiceName(), i + 1, session.getFetchedPaths().size(), System.currentTimeMillis() - started);
            return content;
        }

        // Force a final JSON answer without tools
        messages.add(Map.of(
            "role", "user",
            "content", "Stop calling tools. Return the required analysis JSON now based on files you already fetched."
        ));
        ObjectNode finalPayload = objectMapper.createObjectNode();
        finalPayload.put("model", model);
        finalPayload.put("temperature", 0.1);
        finalPayload.put("max_tokens", 4096);
        finalPayload.set("messages", objectMapper.valueToTree(messages));
        finalPayload.set("response_format", objectMapper.valueToTree(Map.of("type", "json_object")));

        JsonNode finalBody = client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .body(finalPayload)
            .retrieve()
            .body(JsonNode.class);
        String content = finalBody == null ? "" : finalBody.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new AIAnalysisException("OpenAI agent exceeded maxIterations without a final answer");
        }
        log.warn("operation=agentLoop feature=github-agent status=exit forcedFinal=true service={} iterations={} durationMs={}",
            session.getServiceName(), maxIterations, System.currentTimeMillis() - started);
        return content;
    }

    private String executeTool(AgentSession session, String toolName, String argsJson) {
        AgentTool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return "Unknown tool: " + toolName;
        }
        try {
            JsonNode args = objectMapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            return tool.execute(session, args);
        } catch (Exception ex) {
            log.warn("operation=agentTool feature=github-agent status=error tool={} error={}", toolName, ex.getMessage());
            return "Tool error (" + toolName + "): " + ex.getMessage();
        }
    }

    private String agentSystemPrompt() {
        return promptBuilder.getSystemPrompt() + """

            You are a GitHub code agent with tools. Work iteratively:
            1) Call list_repo_files (optionally with query from stack class/file names).
            2) Call get_file_content for the most relevant path(s).
            3) If you need another dependency/file, call get_file_content again.
            4) When you have enough context, stop calling tools and return ONLY the analysis JSON
               (errorSummary, rootCause, suggestedFixes with filePath + full newContent when patching).

            Never invent file paths — only use paths returned by list_repo_files or confirmed via get_file_content.
            Prefer small, correct patches over rewriting unrelated files.
            """;
    }

    private static String agentUserPrompt(String analysisPrompt, AgentSession session) {
        return """
            Service: %s
            Repo: %s
            Branch: %s

            Follow the tool loop (list → get file(s) → refine → JSON).

            %s
            """.formatted(
            session.getServiceName(),
            session.getRepo().fullName(),
            session.getRef(),
            analysisPrompt
        );
    }
}
