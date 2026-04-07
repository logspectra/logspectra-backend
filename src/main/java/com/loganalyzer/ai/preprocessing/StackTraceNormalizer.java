package com.loganalyzer.ai.preprocessing;

import org.springframework.stereotype.Component;

@Component
public class StackTraceNormalizer {

    public String normalize(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return stackTrace;
        }
        return stackTrace
            .replaceAll("\\b0x[0-9a-fA-F]+\\b", "{hex}")
            .replaceAll("\\$Proxy\\d+", "Proxy")
            .replaceAll("(Caused by: )?java\\.[a-zA-Z0-9_.]+:", "java.exception:");
    }
}
