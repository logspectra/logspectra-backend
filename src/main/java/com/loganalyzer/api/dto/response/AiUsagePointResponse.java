package com.loganalyzer.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiUsagePointResponse {
    private String periodLabel;
    private long requested;
    private long cached;
}
