package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.loganalyzer.api.dto.response.DashboardStatsResponse;
import com.loganalyzer.domain.repository.AnalysisHistoryRepository;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardStatsServiceTest {

    @Mock
    private LogElasticsearchRepository logRepository;

    @Mock
    private AnalysisHistoryRepository analysisHistoryRepository;

    @InjectMocks
    private DashboardStatsService dashboardStatsService;

    @Test
    void shouldReturnComputedStatsWhenDataSourcesAvailable() throws IOException {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-25T00:00:00Z");
        Instant to = Instant.parse("2026-03-26T00:00:00Z");

        when(analysisHistoryRepository.countByProjectIdAndCreatedAtBetween(projectId, from, to)).thenReturn(12L);
        when(analysisHistoryRepository.countByProjectIdAndCachedResultTrueAndCreatedAtBetween(projectId, from, to)).thenReturn(5L);
        when(analysisHistoryRepository.sumCostUsdByProjectIdAndCreatedAtBetween(projectId, from, to)).thenReturn(new BigDecimal("6.239"));
        when(logRepository.countLogs(any(), any(), anyString())).thenReturn(1200L, 1000L);
        when(logRepository.countErrors(any(), any(), anyString())).thenReturn(110L, 100L);
        when(logRepository.affectedErrorServices(any(), any(), anyString(), anyInt())).thenReturn(List.of("api", "auth"));

        DashboardStatsResponse response = dashboardStatsService.getStats(projectId, from, to);

        assertEquals(1200L, response.getTotalLogs());
        assertEquals(20, response.getTotalLogsChangePercent());
        assertEquals(110L, response.getErrorCount());
        assertEquals(10, response.getErrorChangePercent());
        assertEquals(2, response.getAffectedServices().getCount());
        assertEquals(List.of("api", "auth"), response.getAffectedServices().getServices());
        assertEquals(12L, response.getAiAnalysesRun());
        assertEquals(5L, response.getAiCached());
        assertEquals(6.24d, response.getAiSaved());
    }

    @Test
    void shouldReturnFallbackStatsWhenAnySourceFails() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-25T00:00:00Z");
        Instant to = Instant.parse("2026-03-26T00:00:00Z");

        when(analysisHistoryRepository.countByProjectIdAndCreatedAtBetween(projectId, from, to)).thenThrow(new RuntimeException("DB unavailable"));

        DashboardStatsResponse response = dashboardStatsService.getStats(projectId, from, to);

        assertEquals(0L, response.getTotalLogs());
        assertEquals(0, response.getTotalLogsChangePercent());
        assertEquals(0L, response.getErrorCount());
        assertEquals(0, response.getErrorChangePercent());
        assertEquals(0, response.getAffectedServices().getCount());
        assertEquals(0L, response.getAiAnalysesRun());
        assertEquals(0L, response.getAiCached());
        assertEquals(0.0d, response.getAiSaved());
    }

    @Test
    void shouldRejectInvalidRange() {
        UUID projectId = UUID.randomUUID();
        Instant same = Instant.parse("2026-03-26T00:00:00Z");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> dashboardStatsService.getStats(projectId, same, same));

        assertEquals("'from' must be before 'to'", ex.getMessage());
    }
}




