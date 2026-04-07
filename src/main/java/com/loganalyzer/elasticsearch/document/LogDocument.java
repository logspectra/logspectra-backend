package com.loganalyzer.elasticsearch.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogDocument {
    private String id;
    private String projectId;
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
    private Boolean resolved;
}
