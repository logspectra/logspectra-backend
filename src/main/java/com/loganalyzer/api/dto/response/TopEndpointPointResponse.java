package com.loganalyzer.api.dto.response;

import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class TopEndpointPointResponse {
    private String name;
    private String endpoint;
    private long value;
    private long count;
}