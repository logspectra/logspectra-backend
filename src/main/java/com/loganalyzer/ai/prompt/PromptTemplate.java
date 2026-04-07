package com.loganalyzer.ai.prompt;

public final class PromptTemplate {
    private PromptTemplate() {
    }

    public static final String SYSTEM_PROMPT = """
        You are an expert backend engineer and SRE specializing in microservices troubleshooting.
        You analyze production log data and provide precise, actionable diagnostics.
        Always respond in valid JSON matching the schema provided.
        Be concise. Do not hallucinate class names or methods not present in the logs.
        """;
}
