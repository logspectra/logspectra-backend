package com.loganalyzer.api.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueGroupResponse {
    private String id;
    private String traceId;
    private String endpoint;
    private String severity;
    private String title;
    private String service;
    private String errorType;
    private String exceptionType;
    private Long occurrences;
    private long occurrenceCount;
    private Instant firstSeen;
    private Instant lastSeen;
    private List<String> affectedServices;
    private List<Integer> trend;
}
