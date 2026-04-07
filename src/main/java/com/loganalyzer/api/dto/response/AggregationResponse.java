package com.loganalyzer.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AggregationResponse {
    private List<ServiceErrorAggregation> services;

    @Data
    @Builder
    public static class ServiceErrorAggregation {
        private String service;
        private long totalErrors;
        private List<ExceptionAggregation> exceptions;
    }

    @Data
    @Builder
    public static class ExceptionAggregation {
        private String exception;
        private long count;
        private List<TimeBucket> timeline;
    }

    @Data
    @Builder
    public static class TimeBucket {
        private String timestamp;
        private long count;
    }
}
