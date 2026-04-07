package com.loganalyzer.infrastructure.exception;

public class InvalidProviderException extends RuntimeException {

    public InvalidProviderException(String message) {
        super(message);
    }
}
