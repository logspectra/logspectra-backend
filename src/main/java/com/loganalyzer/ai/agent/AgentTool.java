package com.loganalyzer.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One GitHub capability the LLM may invoke during the iterative analysis loop.
 */
public interface AgentTool {

    String name();

    String description();

    /** JSON Schema object for OpenAI function {@code parameters}. */
    Map<String, Object> parametersSchema();

    String execute(AgentSession session, JsonNode args);
}
