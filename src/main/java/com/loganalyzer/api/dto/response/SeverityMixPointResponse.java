package com.loganalyzer.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeverityMixPointResponse {
    private String periodLabel;
    private long critical;
    private long high;
    private long medium;
    private long low;
}
