package com.loganalyzer.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private String id;
    private Instant timestamp;
    private String level;
    private String service;
    private String traceId;
    private String spanId;
    private String thread;
    private String logger;
    private String message;
    private String endpoint;
    private String exception;
    private String stackTrace;
    private String host;
    private String environment;
}
