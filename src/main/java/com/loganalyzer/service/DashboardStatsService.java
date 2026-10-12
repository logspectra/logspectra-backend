package com.loganalyzer.service;

import com.loganalyzer.api.dto.response.DashboardStatsResponse;
import com.loganalyzer.domain.repository.AnalysisHistoryRepository;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

    private static final int DEFAULT_WINDOW_HOURS = 24;

    private final LogElasticsearchRepository logRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;

    public DashboardStatsResponse getStats(UUID projectId, Instant from, Instant to) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(Duration.ofHours(DEFAULT_WINDOW_HOURS));

        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        Duration window = Duration.between(effectiveFrom, effectiveTo);
        Instant previousFrom = effectiveFrom.minus(window);
        Instant previousTo = effectiveFrom;
        String projectIdString = projectId.toString();

        try {
            long aiAnalysesRun = analysisHistoryRepository.countByProjectIdAndCreatedAtBetween(projectId, effectiveFrom, effectiveTo);
            long aiCached = analysisHistoryRepository.countByProjectIdAndCachedResultTrueAndCreatedAtBetween(projectId, effectiveFrom, effectiveTo);
            BigDecimal aiSavedReal = analysisHistoryRepository
                .sumCostUsdByProjectIdAndCreatedAtBetween(projectId, effectiveFrom, effectiveTo)
                .setScale(2, RoundingMode.HALF_UP);

            long totalLogs = logRepository.countLogs(effectiveFrom, effectiveTo, projectIdString);
            long previousTotalLogs = logRepository.countLogs(previousFrom, previousTo, projectIdString);
            long errorCount = logRepository.countErrors(effectiveFrom, effectiveTo, projectIdString);
            long previousErrorCount = logRepository.countErrors(previousFrom, previousTo, projectIdString);
            List<String> affectedServices = logRepository.affectedErrorServices(effectiveFrom, effectiveTo, projectIdString, 20);
            List<String> totalServices = logRepository.totalServices(projectIdString);
            return DashboardStatsResponse.builder()
                .totalLogs(totalLogs)
                .totalLogsChangePercent(calculateChangePercent(totalLogs, previousTotalLogs))
                .errorCount(errorCount)
                .errorChangePercent(calculateChangePercent(errorCount, previousErrorCount))
                .totalServices(totalServices)
                .serviceCount(totalServices.size())
                .affectedServices(DashboardStatsResponse.AffectedServices.builder()
                    .count(affectedServices.size())
                    .services(affectedServices)
                    .build())
                .aiAnalysesRun(aiAnalysesRun)
                .aiCached(aiCached)
                .aiSaved(aiSavedReal.doubleValue())
                .build();
        } catch (IOException | RuntimeException ex) {
            log.warn("Dashboard stats unavailable for project {} — returning empty stats", projectId, ex);
            return emptyStats(totalServicesFallback(projectIdString));
        }
    }

    private List<String> totalServicesFallback(String projectIdString) {
        try {
            return logRepository.totalServices(projectIdString);
        } catch (IOException | RuntimeException ex) {
            return Collections.emptyList();
        }
    }

    private DashboardStatsResponse emptyStats(List<String> totalServices) {
        return DashboardStatsResponse.builder()
            .totalLogs(0)
            .totalLogsChangePercent(0)
            .errorCount(0)
            .errorChangePercent(0)
            .totalServices(totalServices)
            .serviceCount(totalServices.size())
            .affectedServices(DashboardStatsResponse.AffectedServices.builder()
                .count(0)
                .services(Collections.emptyList())
                .build())
            .aiAnalysesRun(0)
            .aiCached(0)
            .aiSaved(0)
            .build();
    }

    private int calculateChangePercent(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100 : 0;
        }
        double change = ((double) (current - previous) / previous) * 100d;
        return (int) Math.round(change);
    }
}


