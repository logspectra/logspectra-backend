package com.loganalyzer.api.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
