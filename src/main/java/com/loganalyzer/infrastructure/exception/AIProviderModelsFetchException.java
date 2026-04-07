package com.loganalyzer.infrastructure.exception;

import com.loganalyzer.ai.AIProvider;
import lombok.Getter;

@Getter
public class AIProviderModelsFetchException extends RuntimeException {

    private final AIProvider provider;

    public AIProviderModelsFetchException(AIProvider provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

}

