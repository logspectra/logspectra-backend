package com.loganalyzer.api.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LogSearchResponse {
    private String id;
    private Instant timestamp;
    private String level;
    private String severity;
    private String service;
    private String traceId;
    private String spanId;
    private String thread;
    private String logger;
    private String message;
    private String exception;
    private String stackTrace;
    private String host;
    private String environment;
    private Boolean resolved;
}
