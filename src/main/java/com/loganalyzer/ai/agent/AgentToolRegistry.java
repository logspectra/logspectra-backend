package com.loganalyzer.ai.agent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> byName = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            byName.put(tool.name(), tool);
        }
    }

    public AgentTool get(String name) {
        return byName.get(name);
    }

    public Collection<AgentTool> all() {
        return byName.values();
    }

    public List<Map<String, Object>> openaiToolDefinitions() {
        return byName.values().stream()
            .map(tool -> Map.<String, Object>of(
                "type", "function",
                "function", Map.of(
                    "name", tool.name(),
                    "description", tool.description(),
                    "parameters", tool.parametersSchema()
                )
            ))
            .toList();
    }

    /** Gemini {@code tools[].functionDeclarations}. */
    public List<Map<String, Object>> geminiFunctionDeclarations() {
        return byName.values().stream()
            .map(tool -> Map.<String, Object>of(
                "name", tool.name(),
                "description", tool.description(),
                "parameters", tool.parametersSchema()
            ))
            .toList();
    }

    /** Anthropic {@code tools[]} entries. */
    public List<Map<String, Object>> claudeToolDefinitions() {
        return byName.values().stream()
            .map(tool -> Map.<String, Object>of(
                "name", tool.name(),
                "description", tool.description(),
                "input_schema", tool.parametersSchema()
            ))
            .toList();
    }
}
