package com.loganalyzer.infrastructure.exception;

public class NoActiveAISettingsException extends RuntimeException {
    public NoActiveAISettingsException(String message) {
        super(message);
    }
}
