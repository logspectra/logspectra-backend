package com.loganalyzer.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorTriagePointResponse {
    private String periodLabel;
    private long openCount;
    private long resolvedCount;
}
