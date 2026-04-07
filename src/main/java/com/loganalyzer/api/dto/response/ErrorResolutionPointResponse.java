package com.loganalyzer.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResolutionPointResponse {
    private int hour;
    private long value;
}
