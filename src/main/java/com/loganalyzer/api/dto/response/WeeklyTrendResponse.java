package com.loganalyzer.api.dto.response;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeeklyTrendResponse {
    private List<String> services;
    private List<Point> points;

    @Data
    @Builder
    public static class Point {
        private String periodLabel;
        private double errorRate;
        private Map<String, Long> serviceCounts;
    }
}

