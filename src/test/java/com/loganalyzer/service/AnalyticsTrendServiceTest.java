package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import com.loganalyzer.api.dto.response.AiUsageResponse;
import com.loganalyzer.api.dto.response.ErrorResolutionResponse;
import com.loganalyzer.api.dto.response.IngestionRateResponse;
import com.loganalyzer.api.dto.response.SeverityMixResponse;
import com.loganalyzer.api.dto.response.TopEndpointResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.loganalyzer.domain.model.AnalysisHistory;
import com.loganalyzer.domain.repository.AnalysisHistoryRepository;
import com.loganalyzer.api.dto.response.WeeklyTrendResponse;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsTrendServiceTest {

    @Mock
    private LogElasticsearchRepository logRepository;

    @Mock
    private AnalysisHistoryRepository analysisHistoryRepository;

    @InjectMocks
    private AnalyticsTrendService analyticsTrendService;

    @Test
    void shouldReturnZeroFilledSeriesWhenAggregationFails() throws Exception {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-22T00:00:00Z");
        Instant to = Instant.parse("2026-03-28T23:59:59Z");
        when(logRepository.totalServices(projectId.toString())).thenReturn(List.of("api-gateway", "auth-service"));
        when(logRepository.weeklyTrend(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(projectId.toString())))
            .thenThrow(new IOException("ES unavailable"));

        WeeklyTrendResponse response = analyticsTrendService.getWeeklyTrend(projectId, from, to);

        assertEquals(List.of(), response.getServices());
        assertEquals(7, response.getPoints().size());
        response.getPoints().forEach(point -> assertEquals(0.0d, point.getErrorRate()));
    }

    @Test
    void shouldRejectInvalidRange() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-28T00:00:00Z");
        Instant to = Instant.parse("2026-03-27T00:00:00Z");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> analyticsTrendService.getWeeklyTrend(projectId, from, to));

        assertTrue(ex.getMessage().contains("from"));
    }

    @Test
    void shouldBuildAiUsageResponseWithExpectedShapeAndTotals() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-15T00:00:00Z");
        Instant to = Instant.parse("2026-03-16T23:59:59Z");

        List<AnalysisHistory> rows = List.of(
            AnalysisHistory.builder().projectId(projectId).createdAt(Instant.parse("2026-03-15T10:00:00Z")).cachedResult(false).build(),
            AnalysisHistory.builder().projectId(projectId).createdAt(Instant.parse("2026-03-15T11:00:00Z")).cachedResult(false).build(),
            AnalysisHistory.builder().projectId(projectId).createdAt(Instant.parse("2026-03-15T12:00:00Z")).cachedResult(true).build(),
            AnalysisHistory.builder().projectId(projectId).createdAt(Instant.parse("2026-03-16T10:00:00Z")).cachedResult(false).build(),
            AnalysisHistory.builder().projectId(projectId).createdAt(Instant.parse("2026-03-16T11:00:00Z")).cachedResult(true).build()
        );
        when(analysisHistoryRepository.findByProjectIdAndCreatedAtBetween(
            org.mockito.ArgumentMatchers.eq(projectId),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(rows);

        AiUsageResponse response = analyticsTrendService.getAiUsage(projectId, from, to);

        assertEquals(2, response.getPoints().size());
        assertEquals("2026-03-15", response.getPoints().getFirst().getPeriodLabel());
        assertEquals(3L, response.getPoints().getFirst().getRequested());
        assertEquals(1L, response.getPoints().getFirst().getCached());
        assertEquals("2026-03-16", response.getPoints().get(1).getPeriodLabel());
        assertEquals(2L, response.getPoints().get(1).getRequested());
        assertEquals(1L, response.getPoints().get(1).getCached());
        assertEquals(5L, response.getTotalRequested());
        assertEquals(2L, response.getTotalCached());
        assertEquals(40.0d, response.getCacheHitRate());
    }

    @Test
    void shouldReturnZeroFilledAiUsagePointsWhenNoRows() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-15T00:00:00Z");
        Instant to = Instant.parse("2026-03-16T23:59:59Z");
        when(analysisHistoryRepository.findByProjectIdAndCreatedAtBetween(
            org.mockito.ArgumentMatchers.eq(projectId),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        AiUsageResponse response = analyticsTrendService.getAiUsage(projectId, from, to);

        assertEquals(2, response.getPoints().size());
        assertEquals(0L, response.getTotalRequested());
        assertEquals(0L, response.getTotalCached());
        assertEquals(0.0d, response.getCacheHitRate());
    }

    @Test
    void shouldReturnZeroFilledIngestionRateWhenAggregationFails() throws Exception {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-28T00:00:00Z");
        Instant to = Instant.parse("2026-03-28T01:00:00Z");
        when(logRepository.ingestionRate(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(projectId.toString())))
            .thenThrow(new IOException("ES unavailable"));

        IngestionRateResponse response = analyticsTrendService.getIngestionRate(projectId, from, to);

        assertEquals(2, response.getPoints().size());
        assertEquals("00:00", response.getPoints().getFirst().getPeriodLabel());
        assertEquals(0L, response.getPoints().getFirst().getLevelCounts().get("DEBUG"));
        assertEquals(0L, response.getPoints().getFirst().getLevelCounts().get("INFO"));
        assertEquals(0L, response.getPoints().getFirst().getLevelCounts().get("WARN"));
        assertEquals(0L, response.getPoints().getFirst().getLevelCounts().get("ERROR"));
    }

    @Test
    void shouldRejectInvalidIngestionRange() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-28T02:00:00Z");
        Instant to = Instant.parse("2026-03-28T01:00:00Z");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> analyticsTrendService.getIngestionRate(projectId, from, to));

        assertTrue(ex.getMessage().contains("from"));
    }

    @Test
    void shouldReturnZeroFilledSeverityMixWhenAggregationFails() throws Exception {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-24T00:00:00Z");
        Instant to = Instant.parse("2026-03-25T23:59:59Z");
        when(logRepository.severityMix(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(projectId.toString())))
            .thenThrow(new IOException("ES unavailable"));

        SeverityMixResponse response = analyticsTrendService.getSeverityMix(projectId, from, to);

        assertEquals(2, response.getPoints().size());
        response.getPoints().forEach(point -> {
            assertEquals(0L, point.getCritical());
            assertEquals(0L, point.getHigh());
            assertEquals(0L, point.getMedium());
            assertEquals(0L, point.getLow());
            assertTrue(point.getPeriodLabel() != null && !point.getPeriodLabel().isBlank());
        });
    }

    @Test
    void shouldRejectInvalidSeverityMixRange() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-29T00:00:00Z");
        Instant to = Instant.parse("2026-03-28T00:00:00Z");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> analyticsTrendService.getSeverityMix(projectId, from, to));

        assertTrue(ex.getMessage().contains("from"));
    }

    @Test
    void shouldReturnZeroFilledErrorResolutionWhenAggregationFails() throws Exception {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-28T00:00:00Z");
        Instant to = Instant.parse("2026-03-28T01:00:00Z");
        when(logRepository.errorResolution(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(projectId.toString())))
            .thenThrow(new IOException("ES unavailable"));

        ErrorResolutionResponse response = analyticsTrendService.getErrorResolution(projectId, from, to);

        assertEquals(24, response.getPoints().size());
        assertEquals(0, response.getPoints().getFirst().getHour());
        assertEquals(0L, response.getPoints().getFirst().getValue());
        assertEquals(1, response.getPoints().get(1).getHour());
        assertEquals(0L, response.getPoints().get(1).getValue());
        assertEquals(23, response.getPoints().get(23).getHour());
        assertEquals(0L, response.getPoints().get(23).getValue());
    }

    @Test
    void shouldRejectInvalidErrorResolutionRange() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-28T02:00:00Z");
        Instant to = Instant.parse("2026-03-28T01:00:00Z");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> analyticsTrendService.getErrorResolution(projectId, from, to));

        assertTrue(ex.getMessage().contains("from"));
    }

    @Test
    void shouldReturnEmptyTopEndpointsWhenEndpointDataMissing() throws Exception {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-28T00:00:00Z");
        Instant to = Instant.parse("2026-03-28T01:00:00Z");
        @SuppressWarnings("unchecked")
        SearchResponse<Void> response = Mockito.mock(SearchResponse.class);
        when(response.aggregations()).thenReturn(null);
        when(logRepository.topEndpoints(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(projectId.toString()),
            org.mockito.ArgumentMatchers.eq(5))).thenReturn(response);

        TopEndpointResponse result = analyticsTrendService.getTopEndpoints(projectId, from, to);

        assertTrue(result.getPoints().isEmpty());
    }

    @Test
    void shouldRejectInvalidTopEndpointsRange() {
        UUID projectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-03-28T02:00:00Z");
        Instant to = Instant.parse("2026-03-28T01:00:00Z");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> analyticsTrendService.getTopEndpoints(projectId, from, to));

        assertTrue(ex.getMessage().contains("from"));
    }
}

