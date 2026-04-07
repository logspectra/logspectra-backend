package com.loganalyzer.ai;

import java.util.Arrays;

public enum AIProvider {
    OPENAI,
    OLLAMA,
    CLAUDE;

    public static AIProvider from(String value) {
        return Arrays.stream(values())
            .filter(provider -> provider.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported provider: " + value));
    }
}
