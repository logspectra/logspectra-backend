package com.loganalyzer.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiUsageResponse {
    private List<AiUsagePointResponse> points;
    private long totalRequested;
    private long totalCached;
    private double cacheHitRate;
}
