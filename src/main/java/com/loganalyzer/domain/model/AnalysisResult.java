package com.loganalyzer.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult implements Serializable {
    private String fingerprint;
    private String errorSummary;
    private String rootCause;
    @Builder.Default
    private List<String> affectedServices = new ArrayList<>();
    private String errorPattern;
    private String severity;
    @Builder.Default
    private List<SuggestedFix> suggestedFixes = new ArrayList<>();
    @Builder.Default
    private List<String> relatedTraceIds = new ArrayList<>();
    private double confidence;
    private Instant analyzedAt;
    private boolean cachedResult;
}
