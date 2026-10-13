package com.loganalyzer.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.ai.prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubAgentToolSupport {

    private final AgentToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public String agentSystemPrompt() {
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

    public String agentUserPrompt(String analysisPrompt, AgentSession session) {
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

    public String executeTool(AgentSession session, String toolName, String argsJson) {
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

    public AgentToolRegistry registry() {
        return toolRegistry;
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
