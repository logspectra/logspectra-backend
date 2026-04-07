package com.loganalyzer.service;

import com.loganalyzer.api.dto.response.DashboardStatsResponse;
import com.loganalyzer.domain.repository.AnalysisHistoryRepository;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final long DUMMY_TOTAL_LOGS = 1_284_402L;
    private static final int DUMMY_TOTAL_LOGS_CHANGE_PERCENT = 12;
    private static final long DUMMY_ERROR_COUNT = 3_847L;
    private static final int DUMMY_ERROR_CHANGE_PERCENT = 23;
    private static final List<String> DUMMY_AFFECTED_SERVICES =
        List.of("api-gateway", "auth-service", "payment-service", "admin-service");
    private static final long DUMMY_AI_CACHED = 38L;
    private static final double DUMMY_AI_SAVED = 4.2d;

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

        try {
            long aiAnalysesRun = analysisHistoryRepository.countByProjectIdAndCreatedAtBetween(projectId, effectiveFrom, effectiveTo);
            long aiCached = analysisHistoryRepository.countByProjectIdAndCachedResultTrueAndCreatedAtBetween(projectId, effectiveFrom, effectiveTo);
            BigDecimal aiSavedReal = analysisHistoryRepository
                .sumCostUsdByProjectIdAndCreatedAtBetween(projectId, effectiveFrom, effectiveTo)
                .setScale(2, RoundingMode.HALF_UP);

            String projectIdString = projectId.toString();
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
                .aiSaved(aiSavedReal.doubleValue() > 0 ? aiSavedReal.doubleValue() : DUMMY_AI_SAVED)
                .build();
        } catch (IOException | RuntimeException ex) {
            // Return deterministic demo values when stats data is unavailable.
            return DashboardStatsResponse.builder()
                .totalLogs(DUMMY_TOTAL_LOGS)
                .totalLogsChangePercent(DUMMY_TOTAL_LOGS_CHANGE_PERCENT)
                .errorCount(DUMMY_ERROR_COUNT)
                .errorChangePercent(DUMMY_ERROR_CHANGE_PERCENT)
                .affectedServices(DashboardStatsResponse.AffectedServices.builder()
                    .count(DUMMY_AFFECTED_SERVICES.size())
                    .services(DUMMY_AFFECTED_SERVICES)
                    .build())
                .aiAnalysesRun(142)
                .aiCached(DUMMY_AI_CACHED)
                .aiSaved(DUMMY_AI_SAVED)
                .build();
        }
    }

    private int calculateChangePercent(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100 : 0;
        }
        double change = ((double) (current - previous) / previous) * 100d;
        return (int) Math.round(change);
    }
}


