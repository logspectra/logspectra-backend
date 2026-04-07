package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IssueQueryRequest {
    public static final String GROUP_BY_SERVICE_EXCEPTION = "service_exception";
    public static final String GROUP_BY_TRACE_ID = "traceId";
    public static final String GROUP_BY_ENDPOINT = "endpoint";

    private String window = "1h";
    private String service;
    @Pattern(regexp = "(?i)(service_exception|traceId|endpoint)", message = "groupBy must be service_exception, traceId or endpoint")
    private String groupBy = GROUP_BY_SERVICE_EXCEPTION;
    @Min(0)
    private int page = 0;
    @Min(1)
    @Max(100)
    private int size = 20;
}
