package com.loganalyzer.infrastructure.exception;

public class ElasticsearchServiceException extends RuntimeException {
    public ElasticsearchServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
