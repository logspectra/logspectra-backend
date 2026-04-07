package com.loganalyzer.api.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalysisResultResponse {
    private String fingerprint;
    private String errorSummary;
    private String rootCause;
    private String service;
    private List<String> affectedServices;
    private String errorPattern;
    private String severity;
    private List<SuggestedFixResponse> suggestedFixes;
    private List<String> relatedTraceIds;
    private double confidence;
    private Instant analyzedAt;
    private boolean cachedResult;
    private boolean cached;
}
