package com.loganalyzer.infrastructure.exception;

public class AIQuotaExceededException extends RuntimeException {
    public AIQuotaExceededException(String message) {
        super(message);
    }
}
