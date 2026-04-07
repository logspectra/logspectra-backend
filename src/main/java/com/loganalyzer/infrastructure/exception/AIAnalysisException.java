package com.loganalyzer.infrastructure.exception;

public class AIAnalysisException extends RuntimeException {
    public AIAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }

    public AIAnalysisException(String message) {
        super(message);
    }
}
