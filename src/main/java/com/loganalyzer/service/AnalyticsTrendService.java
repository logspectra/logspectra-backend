package com.loganalyzer.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.loganalyzer.api.dto.response.AiUsagePointResponse;
import com.loganalyzer.api.dto.response.AiUsageResponse;
import com.loganalyzer.api.dto.response.ErrorTriagePointResponse;
import com.loganalyzer.api.dto.response.ErrorTriageResponse;
import com.loganalyzer.api.dto.response.IngestionRatePointResponse;
import com.loganalyzer.api.dto.response.IngestionRateResponse;
import com.loganalyzer.api.dto.response.SeverityMixPointResponse;
import com.loganalyzer.api.dto.response.SeverityMixResponse;
import com.loganalyzer.api.dto.response.TopEndpointPointResponse;
import com.loganalyzer.api.dto.response.TopEndpointResponse;
import com.loganalyzer.api.dto.response.WeeklyTrendResponse;
import com.loganalyzer.domain.model.AnalysisHistory;
import com.loganalyzer.domain.repository.AnalysisHistoryRepository;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AnalyticsTrendService {

    private static final int DEFAULT_DAYS = 7;
    private static final int DEFAULT_HOURS = 24;
    private static final List<String> INGESTION_LEVELS = List.of("DEBUG", "INFO", "WARN", "ERROR");
    private static final DateTimeFormatter HOUR_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final LogElasticsearchRepository logRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;

    public TopEndpointResponse getTopEndpoints(UUID projectId, Instant from, Instant to) {
        Instant effectiveTo = to == null ? utcNow() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_DAYS, ChronoUnit.DAYS) : from;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        try {
            SearchResponse<Void> response = logRepository.topEndpoints(effectiveFrom, effectiveTo, projectId.toString(), 5);
            List<TopEndpointPointResponse> points = extractTopEndpointPoints(response);
            return TopEndpointResponse.builder()
                .points(points)
                .build();
        } catch (IOException ignored) {
            return TopEndpointResponse.builder()
                .points(List.of())
                .build();
        }
    }

    public ErrorTriageResponse getErrorTriage(UUID projectId, Instant from, Instant to) {
        Instant effectiveTo = to == null ? utcNow() : to;
        Instant effectiveFrom = from == null
            ? effectiveTo.minus(DEFAULT_HOURS, ChronoUnit.HOURS)
            : from;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        List<ErrorTriagePointResponse> points = new ArrayList<>();
        try {
            SearchResponse<Void> response = logRepository.errorTriageTrend(
                effectiveFrom, effectiveTo, projectId.toString());
            points = extractErrorTriagePoints(response, effectiveFrom, effectiveTo);
        } catch (IOException ignored) {
            // Return empty points when Elasticsearch is unavailable.
        }

        return ErrorTriageResponse.builder()
            .points(points)
            .build();
    }

    private List<ErrorTriagePointResponse> extractErrorTriagePoints(
        SearchResponse<Void> response,
        Instant rangeFrom,
        Instant rangeTo
    ) {
        if (response == null
            || response.aggregations() == null
            || response.aggregations().get("over_time") == null
            || response.aggregations().get("over_time").dateHistogram() == null
            || response.aggregations().get("over_time").dateHistogram().buckets() == null
            || response.aggregations().get("over_time").dateHistogram().buckets().array() == null) {
            return List.of();
        }

        DateTimeFormatter labelFormatter = triagePeriodLabelFormatter(rangeFrom, rangeTo);
        List<ErrorTriagePointResponse> points = new ArrayList<>();
        for (DateHistogramBucket bucket : response.aggregations().get("over_time").dateHistogram().buckets().array()) {
            long openCount = filterAggDocCount(bucket, "open");
            long resolvedCount = filterAggDocCount(bucket, "resolved");
            Instant bucketTime = Instant.ofEpochMilli(bucket.key());
            String periodLabel = labelFormatter.format(bucketTime);
            points.add(ErrorTriagePointResponse.builder()
                .periodLabel(periodLabel)
                .openCount(openCount)
                .resolvedCount(resolvedCount)
                .build());
        }
        return points;
    }

    private long filterAggDocCount(DateHistogramBucket bucket, String aggName) {
        if (bucket.aggregations() == null || bucket.aggregations().get(aggName) == null) {
            return 0L;
        }
        Aggregate aggregate = bucket.aggregations().get(aggName);
        if (aggregate.filter() == null) {
            return 0L;
        }
        return aggregate.filter().docCount();
    }

    private DateTimeFormatter triagePeriodLabelFormatter(Instant from, Instant to) {
        long hours = Math.max(1, Duration.between(from, to).toHours());
        if (hours <= 24) {
            return HOUR_LABEL_FORMATTER.withZone(ZoneOffset.UTC);
        }
        return DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneOffset.UTC);
    }

    public IngestionRateResponse getIngestionRate(UUID projectId, Instant from, Instant to) {
        Instant effectiveTo = to == null ? utcNow() : to;
        Instant effectiveFrom = from == null
            ? effectiveTo.minus(DEFAULT_HOURS, ChronoUnit.HOURS)
            : from;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        Map<LocalDateTime, Map<String, Long>> byHour = initializeHourlyLevelMap(effectiveFrom, effectiveTo);

        try {
            SearchResponse<Void> response = logRepository.ingestionRate(effectiveFrom, effectiveTo, projectId.toString());
            populateHourlyCounts(response, byHour);
        } catch (IOException ignored) {
            // Return zero-filled points when Elasticsearch is unavailable.
        }

        List<IngestionRatePointResponse> points = byHour.entrySet().stream()
            .map(entry -> IngestionRatePointResponse.builder()
                .periodLabel(entry.getKey().format(HOUR_LABEL_FORMATTER))
                .levelCounts(entry.getValue())
                .build())
            .toList();

        return IngestionRateResponse.builder()
            .points(points)
            .build();
    }

    public SeverityMixResponse getSeverityMix(UUID projectId, Instant from, Instant to) {
        Instant effectiveTo = to == null ? utcNow() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_DAYS, ChronoUnit.DAYS) : from;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        LocalDate startDate = effectiveFrom.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = effectiveTo.atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, SeverityPoint> daily = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            daily.put(date, new SeverityPoint());
        }

        try {
            SearchResponse<Void> response = logRepository.severityMix(effectiveFrom, effectiveTo, projectId.toString());
            populateSeverityMix(response, daily);
        } catch (IOException ignored) {
            // Return zero-filled points when Elasticsearch is unavailable.
        }

        List<SeverityMixPointResponse> points = daily.entrySet().stream()
            .map(entry -> SeverityMixPointResponse.builder()
                .periodLabel(entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                .critical(entry.getValue().critical)
                .high(entry.getValue().high)
                .medium(entry.getValue().medium)
                .low(entry.getValue().low)
                .build())
            .toList();

        return SeverityMixResponse.builder()
            .points(points)
            .build();
    }

    public AiUsageResponse getAiUsage(UUID projectId, Instant from, Instant to) {
        Instant effectiveTo = to == null ? utcNow() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_DAYS, ChronoUnit.DAYS) : from;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        LocalDate startDate = effectiveFrom.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = effectiveTo.atZone(ZoneOffset.UTC).toLocalDate();

        Map<LocalDate, DailyAiUsage> daily = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            daily.put(date, new DailyAiUsage());
        }

        List<AnalysisHistory> rows = analysisHistoryRepository.findByProjectIdAndCreatedAtBetween(projectId, effectiveFrom, effectiveTo);
        for (AnalysisHistory row : rows) {
            if (row.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = row.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            DailyAiUsage usage = daily.get(day);
            if (usage == null) {
                continue;
            }
            usage.requested++;
            if (Boolean.TRUE.equals(row.getCachedResult())) {
                usage.cached++;
            }
        }

        List<AiUsagePointResponse> points = daily.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(entry -> AiUsagePointResponse.builder()
                .periodLabel(entry.getKey().toString())
                .requested(entry.getValue().requested)
                .cached(entry.getValue().cached)
                .build())
            .toList();

        long totalRequested = points.stream().mapToLong(AiUsagePointResponse::getRequested).sum();
        long totalCached = points.stream().mapToLong(AiUsagePointResponse::getCached).sum();
        double cacheHitRate = totalRequested == 0
            ? 0.0d
            : Math.round(((totalCached * 100.0d) / totalRequested) * 100.0d) / 100.0d;

        return AiUsageResponse.builder()
            .points(points)
            .totalRequested(totalRequested)
            .totalCached(totalCached)
            .cacheHitRate(cacheHitRate)
            .build();
    }

    public WeeklyTrendResponse getWeeklyTrend(UUID projectId, Instant from, Instant to) {
        Instant effectiveTo = to == null ? utcNow() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(DEFAULT_DAYS, ChronoUnit.DAYS) : from;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }

        LocalDate startDate = effectiveFrom.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = effectiveTo.atZone(ZoneOffset.UTC).toLocalDate();
        String projectIdText = projectId.toString();

        try {
            List<String> knownServices = logRepository.totalServices(projectIdText);
            SearchResponse<Void> response = logRepository.weeklyTrend(effectiveFrom, effectiveTo, projectIdText);
            return buildWeeklyTrend(response, knownServices, startDate, endDate);
        } catch (IOException ex) {
            return buildWeeklyTrend(null, List.of(), startDate, endDate);
        }
    }

    private WeeklyTrendResponse buildWeeklyTrend(SearchResponse<Void> response, List<String> knownServices, LocalDate startDate, LocalDate endDate) {
        LinkedHashSet<String> serviceSet = new LinkedHashSet<>(knownServices == null ? List.of() : knownServices);
        List<DayMetrics> dayMetrics = extractDayMetrics(response, serviceSet);
        List<String> services = new ArrayList<>(serviceSet);

        Map<LocalDate, DayPoint> dayPoints = initializePoints(startDate, endDate, services);
        for (DayMetrics metrics : dayMetrics) {
            DayPoint point = dayPoints.get(metrics.date());
            if (point == null) {
                continue;
            }
            point.totalLogs = metrics.totalLogs();
            point.errorLogs = metrics.errorLogs();
            point.serviceCounts.putAll(metrics.serviceCounts());
        }

        List<WeeklyTrendResponse.Point> points = dayPoints.entrySet().stream()
            .map(entry -> WeeklyTrendResponse.Point.builder()
                .periodLabel(entry.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                .errorRate(calculateErrorRate(entry.getValue().errorLogs, entry.getValue().totalLogs))
                .serviceCounts(entry.getValue().serviceCounts)
                .build())
            .toList();

        return WeeklyTrendResponse.builder()
            .services(services)
            .points(points)
            .build();
    }

    private List<DayMetrics> extractDayMetrics(SearchResponse<Void> response, LinkedHashSet<String> serviceSet) {
        if (response == null || response.aggregations() == null || response.aggregations().get("by_day") == null
            || response.aggregations().get("by_day").dateHistogram() == null
            || response.aggregations().get("by_day").dateHistogram().buckets() == null
            || response.aggregations().get("by_day").dateHistogram().buckets().array() == null) {
            return List.of();
        }

        List<DayMetrics> dayMetrics = new ArrayList<>();
        for (DateHistogramBucket bucket : response.aggregations().get("by_day").dateHistogram().buckets().array()) {
            String keyAsString = bucket.keyAsString();
            if (keyAsString == null) {
                continue;
            }
            LocalDate date = Instant.parse(keyAsString).atZone(ZoneOffset.UTC).toLocalDate();
            long totalLogs = bucket.docCount();
            long errorLogs = extractFilterCount(bucket.aggregations());
            Map<String, Long> serviceCounts = extractServiceCounts(bucket.aggregations());
            serviceSet.addAll(serviceCounts.keySet());
            dayMetrics.add(new DayMetrics(date, totalLogs, errorLogs, serviceCounts));
        }
        return dayMetrics;
    }

    private long extractFilterCount(Map<String, Aggregate> aggs) {
        if (aggs == null || aggs.get("error_count") == null || aggs.get("error_count").filter() == null) {
            return 0L;
        }
        return aggs.get("error_count").filter().docCount();
    }

    private Map<String, Long> extractServiceCounts(Map<String, Aggregate> aggs) {
        if (aggs == null || aggs.get("by_service") == null || aggs.get("by_service").sterms() == null
            || aggs.get("by_service").sterms().buckets() == null
            || aggs.get("by_service").sterms().buckets().array() == null) {
            return Map.of();
        }

        Map<String, Long> serviceCounts = new LinkedHashMap<>();
        for (StringTermsBucket bucket : aggs.get("by_service").sterms().buckets().array()) {
            if (bucket.key() != null && bucket.key().stringValue() != null) {
                serviceCounts.put(bucket.key().stringValue(), bucket.docCount());
            }
        }
        return serviceCounts;
    }

    private Map<LocalDate, DayPoint> initializePoints(LocalDate startDate, LocalDate endDate, List<String> services) {
        Map<LocalDate, DayPoint> dayPoints = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, Long> serviceCounts = new LinkedHashMap<>();
            for (String service : services) {
                serviceCounts.put(service, 0L);
            }
            dayPoints.put(date, new DayPoint(serviceCounts));
        }
        return dayPoints;
    }

    private double calculateErrorRate(long errorLogs, long totalLogs) {
        if (totalLogs <= 0) {
            return 0.0d;
        }
        double rate = (errorLogs * 100.0d) / totalLogs;
        return Math.round(rate * 10.0d) / 10.0d;
    }

    private record DayMetrics(LocalDate date, long totalLogs, long errorLogs, Map<String, Long> serviceCounts) {
    }

    private static class DayPoint {
        private final Map<String, Long> serviceCounts;
        private long totalLogs;
        private long errorLogs;

        private DayPoint(Map<String, Long> serviceCounts) {
            this.serviceCounts = serviceCounts;
        }
    }

    private static class DailyAiUsage {
        private long requested;
        private long cached;
    }

    private Map<LocalDateTime, Map<String, Long>> initializeHourlyLevelMap(Instant from, Instant to) {
        Map<LocalDateTime, Map<String, Long>> byHour = new LinkedHashMap<>();
        LocalDateTime start = LocalDateTime.ofInstant(from, ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        LocalDateTime end = LocalDateTime.ofInstant(to, ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        for (LocalDateTime hour = start; !hour.isAfter(end); hour = hour.plusHours(1)) {
            Map<String, Long> levels = new LinkedHashMap<>();
            for (String level : INGESTION_LEVELS) {
                levels.put(level, 0L);
            }
            byHour.put(hour, levels);
        }
        return byHour;
    }

    private void populateHourlyCounts(SearchResponse<Void> response, Map<LocalDateTime, Map<String, Long>> byHour) {
        if (response == null || response.aggregations() == null || response.aggregations().get("by_hour") == null
            || response.aggregations().get("by_hour").dateHistogram() == null
            || response.aggregations().get("by_hour").dateHistogram().buckets() == null
            || response.aggregations().get("by_hour").dateHistogram().buckets().array() == null) {
            return;
        }

        for (DateHistogramBucket bucket : response.aggregations().get("by_hour").dateHistogram().buckets().array()) {
            String keyAsString = bucket.keyAsString();
            if (keyAsString == null) {
                continue;
            }
            LocalDateTime hour = Instant.parse(keyAsString).atZone(ZoneOffset.UTC).toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
            Map<String, Long> levels = byHour.get(hour);
            if (levels == null || bucket.aggregations() == null || bucket.aggregations().get("by_level") == null
                || bucket.aggregations().get("by_level").sterms() == null
                || bucket.aggregations().get("by_level").sterms().buckets() == null
                || bucket.aggregations().get("by_level").sterms().buckets().array() == null) {
                continue;
            }

            for (StringTermsBucket levelBucket : bucket.aggregations().get("by_level").sterms().buckets().array()) {
                if (levelBucket.key() == null || levelBucket.key().stringValue() == null) {
                    continue;
                }
                String level = levelBucket.key().stringValue().toUpperCase(Locale.ENGLISH);
                if (levels.containsKey(level)) {
                    levels.put(level, levelBucket.docCount());
                }
            }
        }
    }

    private void populateSeverityMix(SearchResponse<Void> response, Map<LocalDate, SeverityPoint> daily) {
        if (response == null || response.aggregations() == null || response.aggregations().get("by_day") == null
            || response.aggregations().get("by_day").dateHistogram() == null
            || response.aggregations().get("by_day").dateHistogram().buckets() == null
            || response.aggregations().get("by_day").dateHistogram().buckets().array() == null) {
            return;
        }

        for (DateHistogramBucket dayBucket : response.aggregations().get("by_day").dateHistogram().buckets().array()) {
            String keyAsString = dayBucket.keyAsString();
            if (keyAsString == null) {
                continue;
            }
            LocalDate date = Instant.parse(keyAsString).atZone(ZoneOffset.UTC).toLocalDate();
            SeverityPoint point = daily.get(date);
            if (point == null || dayBucket.aggregations() == null || dayBucket.aggregations().get("by_level") == null
                || dayBucket.aggregations().get("by_level").sterms() == null
                || dayBucket.aggregations().get("by_level").sterms().buckets() == null
                || dayBucket.aggregations().get("by_level").sterms().buckets().array() == null) {
                continue;
            }

            for (StringTermsBucket levelBucket : dayBucket.aggregations().get("by_level").sterms().buckets().array()) {
                if (levelBucket.key() == null || levelBucket.key().stringValue() == null) {
                    continue;
                }
                String level = levelBucket.key().stringValue().toUpperCase(Locale.ENGLISH);
                long count = levelBucket.docCount();
                switch (level) {
                    case "CRITICAL", "FATAL" -> point.critical += count;
                    case "ERROR" -> point.high += count;
                    case "WARN", "WARNING" -> point.medium += count;
                    default -> point.low += count;
                }
            }
        }
    }

    private List<TopEndpointPointResponse> extractTopEndpointPoints(SearchResponse<Void> response) {
        if (response == null || response.aggregations() == null || response.aggregations().get("by_endpoint") == null
            || response.aggregations().get("by_endpoint").sterms() == null
            || response.aggregations().get("by_endpoint").sterms().buckets() == null
            || response.aggregations().get("by_endpoint").sterms().buckets().array() == null) {
            return List.of();
        }

        return response.aggregations().get("by_endpoint").sterms().buckets().array().stream()
            .filter(bucket -> bucket.key() != null && StringUtils.hasText(bucket.key().stringValue()))
            .map(bucket -> {
                String endpoint = bucket.key().stringValue();
                long count = bucket.docCount();
                return TopEndpointPointResponse.builder()
                    .name(endpoint)
                    .endpoint(endpoint)
                    .value(count)
                    .count(count)
                    .build();
            })
            .toList();
    }

    private static class SeverityPoint {
        private long critical;
        private long high;
        private long medium;
        private long low;
    }

    private Instant utcNow() {
        return Instant.now(Clock.systemUTC());
    }
}


