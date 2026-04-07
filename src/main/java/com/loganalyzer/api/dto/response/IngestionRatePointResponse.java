package com.loganalyzer.api.dto.response;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngestionRatePointResponse {
    private String periodLabel;
    private Map<String, Long> levelCounts;
}
