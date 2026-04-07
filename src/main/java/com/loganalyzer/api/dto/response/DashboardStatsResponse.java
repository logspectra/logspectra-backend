package com.loganalyzer.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    private long totalLogs;
    private int totalLogsChangePercent;
    private long errorCount;
    private int errorChangePercent;
    private AffectedServices affectedServices;
    private long aiAnalysesRun;
    private long aiCached;
    private double aiSaved;
    private int serviceCount;
    private List<String> totalServices;

    @Data
    @Builder
    public static class AffectedServices {
        private int count;
        private List<String> services;
    }
}

