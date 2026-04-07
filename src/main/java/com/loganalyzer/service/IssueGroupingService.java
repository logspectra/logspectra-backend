package com.loganalyzer.service;

import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.loganalyzer.api.dto.request.IssueQueryRequest;
import com.loganalyzer.api.dto.response.IssueGroupResponse;
import com.loganalyzer.domain.model.SeverityRule;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class IssueGroupingService {

    private final LogElasticsearchRepository logElasticsearchRepository;
    private final CacheService cacheService;
    private final SeverityRuleService severityRuleService;

    public List<IssueGroupResponse> getGroupedIssues(IssueQueryRequest request, UUID projectId) {
        String groupBy = normalizeGroupBy(request.getGroupBy());
        String cacheKey = request.getWindow() + ":" + request.getService() + ":" + groupBy + ":" + request.getPage() + ":" + request.getSize();
        return cacheService.getIssues(projectId.toString(), cacheKey).orElseGet(() -> loadIssues(request, cacheKey, projectId));
    }

    private List<IssueGroupResponse> loadIssues(IssueQueryRequest request, String cacheKey, UUID projectId) {
        List<IssueGroupResponse> response = fetchGroupedIssuesFromLogs(request, projectId);
        cacheService.storeIssues(projectId.toString(), cacheKey, response);
        return response;
    }

    private List<IssueGroupResponse> fetchGroupedIssuesFromLogs(IssueQueryRequest request, UUID projectId) {
        Instant effectiveTo = Instant.now();
        Instant from = effectiveTo.minus(parseWindow(request.getWindow()));
        String groupBy = normalizeGroupBy(request.getGroupBy());
        try {
            SearchResponse<Void> response = IssueQueryRequest.GROUP_BY_TRACE_ID.equalsIgnoreCase(groupBy)
                ? logElasticsearchRepository.aggregateIssueGroupsByTrace(from, effectiveTo, projectId.toString(), request.getService())
                : IssueQueryRequest.GROUP_BY_ENDPOINT.equalsIgnoreCase(groupBy)
                ? logElasticsearchRepository.aggregateIssueGroupsByEndpoint(from, effectiveTo, projectId.toString(), request.getService())
                : logElasticsearchRepository.aggregateIssueGroups(from, effectiveTo, projectId.toString(), request.getService());
            List<SeverityRule> severityRules = severityRuleService.getRulesForResolution();
            List<IssueGroupResponse> grouped = IssueQueryRequest.GROUP_BY_TRACE_ID.equalsIgnoreCase(groupBy)
                ? flattenTraceBuckets(response, from, effectiveTo, effectiveTo, severityRules)
                : IssueQueryRequest.GROUP_BY_ENDPOINT.equalsIgnoreCase(groupBy)
                ? flattenEndpointBuckets(response, from, effectiveTo, effectiveTo, severityRules)
                : flattenServiceExceptionBuckets(response, from, effectiveTo, effectiveTo, severityRules);
            int offset = request.getPage() * request.getSize();
            if (offset >= grouped.size()) {
                return List.of();
            }
            int toIndex = Math.min(offset + request.getSize(), grouped.size());
            return grouped.subList(offset, toIndex);
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<IssueGroupResponse> flattenServiceExceptionBuckets(SearchResponse<Void> response,
                                                                    Instant rangeFrom,
                                                                    Instant rangeTo,
                                                                    Instant fallbackTime,
                                                                    List<SeverityRule> severityRules) {
        if (response.aggregations() == null || response.aggregations().get("by_error_type") == null
            || response.aggregations().get("by_error_type").sterms() == null
            || response.aggregations().get("by_error_type").sterms().buckets() == null
            || response.aggregations().get("by_error_type").sterms().buckets().array() == null) {
            return List.of();
        }

        List<IssueGroupResponse> results = new ArrayList<>();
        for (StringTermsBucket errorTypeBucket : response.aggregations().get("by_error_type").sterms().buckets().array()) {
            String errorType = safeBucketKey(errorTypeBucket, "UnknownError");
            
            // Get all affected services for this error type
            List<String> allAffectedServices = extractAllAffectedServices(errorTypeBucket.aggregations());
            
            // Create separate response for each service-error combination
            if (errorTypeBucket.aggregations() != null && errorTypeBucket.aggregations().get("by_service") != null
                && errorTypeBucket.aggregations().get("by_service").sterms() != null
                && errorTypeBucket.aggregations().get("by_service").sterms().buckets() != null
                && errorTypeBucket.aggregations().get("by_service").sterms().buckets().array() != null) {
                
                for (StringTermsBucket serviceBucket : errorTypeBucket.aggregations().get("by_service").sterms().buckets().array()) {
                    IssueGroupResponse response_item = mapServiceErrorBucket(
                        errorType, serviceBucket, allAffectedServices, rangeFrom, rangeTo, fallbackTime, severityRules);
                    results.add(response_item);
                }
            }
        }
        
        return results.stream()
            .sorted(Comparator.comparingLong(IssueGroupResponse::getOccurrenceCount).reversed())
            .toList();
    }

    private List<IssueGroupResponse> flattenTraceBuckets(SearchResponse<Void> response,
                                                         Instant rangeFrom,
                                                         Instant rangeTo,
                                                         Instant fallbackTime,
                                                         List<SeverityRule> severityRules) {
        if (response.aggregations() == null || response.aggregations().get("by_trace_id") == null
            || response.aggregations().get("by_trace_id").sterms() == null
            || response.aggregations().get("by_trace_id").sterms().buckets() == null
            || response.aggregations().get("by_trace_id").sterms().buckets().array() == null) {
            return List.of();
        }

        return response.aggregations().get("by_trace_id").sterms().buckets().array().stream()
            .map(traceBucket -> mapTraceBucket(traceBucket, rangeFrom, rangeTo, fallbackTime, severityRules))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingLong(IssueGroupResponse::getOccurrenceCount).reversed())
            .toList();
    }

    private List<IssueGroupResponse> flattenEndpointBuckets(SearchResponse<Void> response,
                                                            Instant rangeFrom,
                                                            Instant rangeTo,
                                                            Instant fallbackTime,
                                                            List<SeverityRule> severityRules) {
        if (response.aggregations() == null || response.aggregations().get("by_endpoint") == null
            || response.aggregations().get("by_endpoint").sterms() == null
            || response.aggregations().get("by_endpoint").sterms().buckets() == null
            || response.aggregations().get("by_endpoint").sterms().buckets().array() == null) {
            return List.of();
        }

        List<IssueGroupResponse> results = new ArrayList<>();
        for (StringTermsBucket endpointBucket : response.aggregations().get("by_endpoint").sterms().buckets().array()) {
            String endpoint = safeBucketKey(endpointBucket, null);
            if (!StringUtils.hasText(endpoint)
                || endpointBucket.aggregations() == null
                || endpointBucket.aggregations().get("by_exception") == null
                || endpointBucket.aggregations().get("by_exception").sterms() == null
                || endpointBucket.aggregations().get("by_exception").sterms().buckets() == null
                || endpointBucket.aggregations().get("by_exception").sterms().buckets().array() == null) {
                continue;
            }

            for (StringTermsBucket exceptionBucket : endpointBucket.aggregations().get("by_exception").sterms().buckets().array()) {
                results.add(mapEndpointExceptionBucket(endpoint, exceptionBucket, rangeFrom, rangeTo, fallbackTime, severityRules));
            }
        }

        return results.stream()
            .sorted(Comparator.comparingLong(IssueGroupResponse::getOccurrenceCount).reversed())
            .toList();
    }

    private IssueGroupResponse mapServiceErrorBucket(String errorType, StringTermsBucket serviceBucket, 
                                                      List<String> allAffectedServices,
                                                      Instant rangeFrom,
                                                      Instant rangeTo,
                                                      Instant fallbackTime,
                                                      List<SeverityRule> severityRules) {
        String service = safeBucketKey(serviceBucket, "unknown-service");
        long occurrences = serviceBucket.docCount();
        
        // Get timestamps from service-level aggregations
        Instant firstSeen = extractTime(serviceBucket.aggregations(), "first_seen", fallbackTime);
        Instant lastSeen = extractTime(serviceBucket.aggregations(), "last_seen", fallbackTime);
        String dominantLevel = dominantLevel(serviceBucket.aggregations() == null ? null : serviceBucket.aggregations().get("by_level"));
        String severity = severityRuleService.resolveSeverity(severityRules, errorType, service, dominantLevel);
        
        // Create unique ID for this service-error combination
        String fingerprintRaw = errorType + "|" + service;
        UUID id = UUID.nameUUIDFromBytes(fingerprintRaw.getBytes(StandardCharsets.UTF_8));

        // Extract 6-hour trend data
        List<Integer> trend = extractTrend(serviceBucket.aggregations(), rangeFrom, rangeTo);

        return IssueGroupResponse.builder()
            .id(id.toString())
            .traceId(null)
            .service(service)
            .severity(severity)
            .title(errorType)
            .errorType(errorType)
            .exceptionType(errorType)
            .occurrenceCount(occurrences)
            .occurrences(occurrences)
            .firstSeen(firstSeen)
            .lastSeen(lastSeen)
            .affectedServices(allAffectedServices)
            .trend(trend)
            .build();
    }

    private IssueGroupResponse mapTraceBucket(StringTermsBucket traceBucket,
                                              Instant rangeFrom,
                                              Instant rangeTo,
                                              Instant fallbackTime,
                                              List<SeverityRule> severityRules) {
        String traceId = safeBucketKey(traceBucket, "unknown-trace");
        if (!StringUtils.hasText(traceId)) {
            return null;
        }

        String service = firstService(traceBucket.aggregations());
        String errorType = firstException(traceBucket.aggregations());
        long occurrences = traceBucket.docCount();
        Instant firstSeen = extractTime(traceBucket.aggregations(), "first_seen", fallbackTime);
        Instant lastSeen = extractTime(traceBucket.aggregations(), "last_seen", fallbackTime);
        String dominantLevel = dominantLevel(traceBucket.aggregations() == null ? null : traceBucket.aggregations().get("by_level"));
        String severity = severityRuleService.resolveSeverity(severityRules, errorType, service, dominantLevel);
        List<Integer> trend = extractTrend(traceBucket.aggregations(), rangeFrom, rangeTo);
        List<String> affectedServices = extractAllAffectedServices(traceBucket.aggregations());

        UUID id = UUID.nameUUIDFromBytes(("trace|" + traceId).getBytes(StandardCharsets.UTF_8));
        return IssueGroupResponse.builder()
            .id(id.toString())
            .traceId(traceId)
            .service(service)
            .severity(severity)
            .title(traceId)
            .errorType(errorType)
            .exceptionType(errorType)
            .occurrenceCount(occurrences)
            .occurrences(occurrences)
            .firstSeen(firstSeen)
            .lastSeen(lastSeen)
            .affectedServices(affectedServices)
            .trend(trend)
            .build();
    }

    private IssueGroupResponse mapEndpointExceptionBucket(String endpoint,
                                                          StringTermsBucket exceptionBucket,
                                                          Instant rangeFrom,
                                                          Instant rangeTo,
                                                          Instant fallbackTime,
                                                          List<SeverityRule> severityRules) {
        String errorType = safeBucketKey(exceptionBucket, "UnknownError");
        String service = firstService(exceptionBucket.aggregations());
        long occurrences = exceptionBucket.docCount();
        Instant firstSeen = extractTime(exceptionBucket.aggregations(), "first_seen", fallbackTime);
        Instant lastSeen = extractTime(exceptionBucket.aggregations(), "last_seen", fallbackTime);
        String dominantLevel = dominantLevel(exceptionBucket.aggregations() == null ? null : exceptionBucket.aggregations().get("by_level"));
        String severity = severityRuleService.resolveSeverity(severityRules, errorType, service, dominantLevel);
        List<Integer> trend = extractTrend(exceptionBucket.aggregations(), rangeFrom, rangeTo);
        List<String> affectedServices = extractAllAffectedServices(exceptionBucket.aggregations());

        UUID id = UUID.nameUUIDFromBytes(("endpoint|" + endpoint + "|" + errorType + "|" + service).getBytes(StandardCharsets.UTF_8));
        return IssueGroupResponse.builder()
            .id(id.toString())
            .endpoint(endpoint)
            .traceId(null)
            .service(service)
            .severity(severity)
            .title(errorType)
            .errorType(errorType)
            .exceptionType(errorType)
            .occurrenceCount(occurrences)
            .occurrences(occurrences)
            .firstSeen(firstSeen)
            .lastSeen(lastSeen)
            .affectedServices(affectedServices)
            .trend(trend)
            .build();
    }

    private String firstService(Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggs) {
        if (aggs == null || aggs.get("by_service") == null || aggs.get("by_service").sterms() == null
            || aggs.get("by_service").sterms().buckets() == null || aggs.get("by_service").sterms().buckets().array() == null
            || aggs.get("by_service").sterms().buckets().array().isEmpty()) {
            return "unknown-service";
        }
        return safeBucketKey(aggs.get("by_service").sterms().buckets().array().getFirst(), "unknown-service");
    }

    private String firstException(Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggs) {
        if (aggs == null || aggs.get("by_exception") == null || aggs.get("by_exception").sterms() == null
            || aggs.get("by_exception").sterms().buckets() == null || aggs.get("by_exception").sterms().buckets().array() == null
            || aggs.get("by_exception").sterms().buckets().array().isEmpty()) {
            return "UnknownError";
        }
        return safeBucketKey(aggs.get("by_exception").sterms().buckets().array().getFirst(), "UnknownError");
    }

    private String normalizeGroupBy(String groupBy) {
        if (IssueQueryRequest.GROUP_BY_TRACE_ID.equalsIgnoreCase(groupBy)) {
            return IssueQueryRequest.GROUP_BY_TRACE_ID;
        }
        if (IssueQueryRequest.GROUP_BY_ENDPOINT.equalsIgnoreCase(groupBy)) {
            return IssueQueryRequest.GROUP_BY_ENDPOINT;
        }
        return IssueQueryRequest.GROUP_BY_SERVICE_EXCEPTION;
    }

    private List<Integer> extractTrend(Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggs,
                                       Instant rangeFrom,
                                       Instant rangeTo) {
        Instant alignedFrom = alignToSixHourBoundary(rangeFrom);
        Instant alignedTo = alignToSixHourBoundary(rangeTo);
        int bucketCount = (int) (Duration.between(alignedFrom, alignedTo).toHours() / 6) + 1;
        List<Integer> zeroFilled = new ArrayList<>(Collections.nCopies(Math.max(bucketCount, 1), 0));

        if (aggs == null || aggs.get("trend_6h") == null
            || aggs.get("trend_6h").dateHistogram() == null
            || aggs.get("trend_6h").dateHistogram().buckets() == null
            || aggs.get("trend_6h").dateHistogram().buckets().array() == null) {
            return zeroFilled;
        }

        aggs.get("trend_6h").dateHistogram().buckets().array().forEach(bucket -> {
            Instant bucketTime = Instant.ofEpochMilli(bucket.key());
            if (bucketTime.isBefore(alignedFrom) || bucketTime.isAfter(alignedTo)) {
                return;
            }
            int index = (int) (Duration.between(alignedFrom, bucketTime).toHours() / 6);
            if (index >= 0 && index < zeroFilled.size()) {
                zeroFilled.set(index, (int) bucket.docCount());
            }
        });
        return zeroFilled;
    }

    private Instant alignToSixHourBoundary(Instant instant) {
        Instant hourTruncated = instant.truncatedTo(ChronoUnit.HOURS);
        long hourOfDay = hourTruncated.atZone(ZoneOffset.UTC).getHour();
        return hourTruncated.minus(Duration.ofHours(hourOfDay % 6));
    }

    private List<String> extractAllAffectedServices(Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggs) {
        if (aggs == null || aggs.get("all_affected_services") == null
            || aggs.get("all_affected_services").sterms() == null
            || aggs.get("all_affected_services").sterms().buckets() == null
            || aggs.get("all_affected_services").sterms().buckets().array() == null) {
            return List.of();
        }

        return aggs.get("all_affected_services").sterms().buckets().array().stream()
            .map(bucket -> safeBucketKey(bucket, null))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    }

    private String safeBucketKey(StringTermsBucket bucket, String fallback) {
        return bucket.key() == null || !StringUtils.hasText(bucket.key().stringValue()) ? fallback : bucket.key().stringValue();
    }

    private Instant extractTime(Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggs, String key, Instant fallback) {
        if (aggs == null || aggs.get(key) == null) {
            return fallback;
        }
        co.elastic.clients.elasticsearch._types.aggregations.Aggregate aggregate = aggs.get(key);
        double value;
        if (aggregate.isMin() && aggregate.min() != null) {
            value = aggregate.min().value();
        } else if (aggregate.isMax() && aggregate.max() != null) {
            value = aggregate.max().value();
        } else {
            return fallback;
        }
        if (Double.isNaN(value)) {
            return fallback;
        }
        return Instant.ofEpochMilli((long) value);
    }

    private Duration parseWindow(String window) {
        if (window == null || window.isBlank()) {
            return Duration.ofHours(1);
        }

        String normalized = window.trim().toLowerCase(Locale.ENGLISH).replaceAll("\\s+", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("^(\\d+)(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)$")
            .matcher(normalized);
        if (!matcher.matches()) {
            return Duration.ofHours(1);
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        if (unit.startsWith("m")) {
            return Duration.ofMinutes(value);
        }
        if (unit.startsWith("h")) {
            return Duration.ofHours(value);
        }
        return Duration.ofDays(value);
    }

    private String dominantLevel(co.elastic.clients.elasticsearch._types.aggregations.Aggregate levelAgg) {
        String dominant = null;
        long dominantCount = -1;
        if (levelAgg != null && levelAgg.sterms() != null && levelAgg.sterms().buckets() != null && levelAgg.sterms().buckets().array() != null) {
            StringTermsAggregate aggregate = levelAgg.sterms();
            for (StringTermsBucket bucket : aggregate.buckets().array()) {
                if (bucket.key() == null || !StringUtils.hasText(bucket.key().stringValue())) {
                    continue;
                }
                if (bucket.docCount() > dominantCount) {
                    dominantCount = bucket.docCount();
                    dominant = bucket.key().stringValue();
                }
            }
        }
        return dominant;
    }
}
