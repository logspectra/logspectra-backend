package com.loganalyzer.elasticsearch.query;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.FieldDateMath;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import com.loganalyzer.api.dto.request.AggregationRequest;
import com.loganalyzer.api.dto.request.LogSearchRequest;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LogQueryBuilder {

    private static final String INDEX_PATTERN = "app-logs-*";

    private final ElasticsearchClient client;

    public LogQueryBuilder(ElasticsearchClient client) {
        this.client = client;
    }

    public SearchResponse<LogDocument> searchLogs(LogSearchRequest req, String projectId) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .query(q -> q.bool(b -> {
                b.filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)));
                if (StringUtils.hasText(req.getLevel())) {
                    b.filter(f -> f.term(t -> t.field("level.keyword").value(req.getLevel())));
                }
                if (StringUtils.hasText(req.getService())) {
                    b.filter(f -> f.term(t -> t.field("service.keyword").value(req.getService())));
                }
                if (StringUtils.hasText(req.getTraceId())) {
                    String traceId = req.getTraceId().trim();
                    b.filter(f -> f.bool(bb -> bb
                        .should(sh -> sh.term(t -> t.field("traceId.keyword").value(traceId)))
                        .should(sh -> sh.term(t -> t.field("traceId").value(traceId)))
                        .minimumShouldMatch("1")));
                }
                if (StringUtils.hasText(req.getEnvironment())) {
                    b.filter(f -> f.term(t -> t.field("environment.keyword").value(req.getEnvironment())));
                }
                b.filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(req.getFrom().toString())
                    .lte(req.getTo().toString()))));
                if (StringUtils.hasText(req.getQuery())) {
                    String searchQuery = req.getQuery().trim();
                    b.must(m -> m.bool(bb -> bb
                        .should(sh -> sh.match(match -> match.field("message").query(searchQuery).fuzziness("AUTO")))
                        .should(sh -> sh.term(t -> t.field("traceId.keyword").value(searchQuery)))
                        .should(sh -> sh.term(t -> t.field("traceId").value(searchQuery)))
                        .minimumShouldMatch("1")));
                }
                return b;
            }))
            .sort(so -> so.field(f -> f.field("timestamp").order(SortOrder.Desc)))
            .from(req.getPage() * req.getSize())
            .size(req.getSize()), LogDocument.class);
    }

    public SearchResponse<Void> aggregateErrors(AggregationRequest request, String projectId) throws IOException {
        Instant rangeFrom = request.getFrom();
        Instant rangeTo = request.getTo();
        String histogramInterval = errorHistogramInterval(rangeFrom, rangeTo);
        String rangeFromIso = rangeFrom.toString();
        String rangeToIso = rangeTo.toString();
        FieldDateMath boundsMin = FieldDateMath.of(fdm -> fdm.expr(rangeFromIso));
        FieldDateMath boundsMax = FieldDateMath.of(fdm -> fdm.expr(rangeToIso));
        return client.search(s -> s.index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.term(t -> t.field("level.keyword").value("ERROR")))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(rangeFromIso)
                    .lte(rangeToIso))))))
            .aggregations("by_service", a -> a
                .terms(t -> t.field("service.keyword").size(20))
                .aggregations("by_exception", aa -> aa
                    .terms(tt -> tt.field("exception.keyword").size(10))
                    .aggregations("over_time", aaa -> aaa.dateHistogram(dh -> dh
                        .field("timestamp")
                        .timeZone("+00:00")
                        .fixedInterval(fi -> fi.time(histogramInterval))
                        .minDocCount(0)
                        .extendedBounds(eb -> eb.min(boundsMin).max(boundsMax))))))
            , Void.class);
    }

    static String errorHistogramInterval(Instant from, Instant to) {
        long seconds = Math.max(1, Duration.between(from, to).getSeconds());
        if (seconds <= 3600) {
            return "1s";
        }
        if (seconds <= 3 * 3600) {
            return "10s";
        }
        if (seconds <= 24 * 3600) {
            return "1m";
        }
        if (seconds <= 4 * 24 * 3600) {
            return "15m";
        }
        return "1h";
    }

    public List<LogDocument> fetchLogsForAnalysis(String traceId, String projectId) throws IOException {
        SearchResponse<LogDocument> response = client.search(s -> s.index(INDEX_PATTERN)
            .query(q -> q.bool(b -> b
                .filter(f -> f.bool(bb -> bb
                    .should(sh -> sh.term(t -> t.field("traceId.keyword").value(traceId)))
                    .should(sh -> sh.term(t -> t.field("traceId").value(traceId)))
                    .minimumShouldMatch("1")))
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))))
            .sort(so -> so.field(f -> f.field("timestamp").order(SortOrder.Asc)))
            .size(200), LogDocument.class);
        return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
    }

    public SearchResponse<LogDocument> recentErrors(int minutes, String projectId) throws IOException {
        Instant from = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        return client.search(s -> s.index(INDEX_PATTERN)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.term(t -> t.field("level.keyword").value("ERROR")))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString()))))))
            .sort(so -> so.field(f -> f.field("timestamp").order(SortOrder.Desc)))
            .size(500), LogDocument.class);
    }

    public long countLogs(Instant from, Instant to, String projectId) throws IOException {
        CountResponse response = client.count(c -> c
            .index(INDEX_PATTERN)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString())))))));
        return response.count();
    }

    public long countErrors(Instant from, Instant to, String projectId) throws IOException {
        CountResponse response = client.count(c -> c
            .index(INDEX_PATTERN)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.term(t -> t.field("level.keyword").value("ERROR")))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString())))))));
        return response.count();
    }

    public List<String> affectedErrorServices(Instant from, Instant to, String projectId, int limit) throws IOException {
        SearchResponse<Void> response = client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.term(t -> t.field("level.keyword").value("ERROR")))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))))))
            .aggregations("by_service", a -> a.terms(t -> t.field("service.keyword").size(limit))),
            Void.class);

        if (response.aggregations() == null
            || response.aggregations().get("by_service") == null
            || response.aggregations().get("by_service").sterms() == null
            || response.aggregations().get("by_service").sterms().buckets() == null
            || response.aggregations().get("by_service").sterms().buckets().array() == null) {
            return Collections.emptyList();
        }

        return response.aggregations().get("by_service").sterms().buckets().array().stream()
            .map(StringTermsBucket::key)
            .filter(Objects::nonNull)
            .map(FieldValue::stringValue)
            .toList();
    }

    public SearchResponse<Void> aggregateIssueGroups(Instant from, Instant to, String projectId, String service) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> {
                b.filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)));
                b.filter(f -> f.terms(t -> t.field("level.keyword")
                    .terms(tv -> tv.value(List.of(FieldValue.of("ERROR"), FieldValue.of("WARN"))))));
                b.filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))));
                if (StringUtils.hasText(service)) {
                    b.filter(f -> f.term(t -> t.field("service.keyword").value(service)));
                }
                return b;
            }))
            .aggregations("by_error_type", a -> a.terms(t -> t.field("exception.keyword").missing("UnknownError").size(1000))
                .aggregations("by_service", aa -> aa.terms(t -> t.field("service.keyword").size(1000))
                    .aggregations("first_seen", aaa -> aaa.min(m -> m.field("timestamp")))
                    .aggregations("last_seen", aaa -> aaa.max(m -> m.field("timestamp")))
                    .aggregations("by_level", aaa -> aaa.terms(t -> t.field("level.keyword").size(2)))
                    .aggregations("trend_6h", aaa -> aaa.dateHistogram(dh -> dh.field("timestamp").timeZone("+00:00").fixedInterval(fi -> fi.time("6h")))))
                .aggregations("all_affected_services", aa -> aa.terms(t -> t.field("service.keyword").size(1000)))
                .aggregations("first_seen", aa -> aa.min(m -> m.field("timestamp")))
                .aggregations("last_seen", aa -> aa.max(m -> m.field("timestamp")))
                .aggregations("by_level", aa -> aa.terms(t -> t.field("level.keyword").size(2)))),
            Void.class);
    }

    public SearchResponse<Void> aggregateIssueGroupsByTrace(Instant from, Instant to, String projectId, String service) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> {
                b.filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)));
                b.filter(f -> f.terms(t -> t.field("level.keyword")
                    .terms(tv -> tv.value(List.of(FieldValue.of("ERROR"), FieldValue.of("WARN"))))));
                b.filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))));
                b.filter(f -> f.exists(e -> e.field("traceId")));
                b.mustNot(m -> m.term(t -> t.field("traceId.keyword").value("")));
                if (StringUtils.hasText(service)) {
                    b.filter(f -> f.term(t -> t.field("service.keyword").value(service)));
                }
                return b;
            }))
            .aggregations("by_trace_id", a -> a.terms(t -> t.field("traceId.keyword").size(1000))
                .aggregations("by_service", aa -> aa.terms(t -> t.field("service.keyword").size(1000)))
                .aggregations("by_exception", aa -> aa.terms(t -> t.field("exception.keyword").missing("UnknownError").size(20)))
                .aggregations("all_affected_services", aa -> aa.terms(t -> t.field("service.keyword").size(1000)))
                .aggregations("first_seen", aa -> aa.min(m -> m.field("timestamp")))
                .aggregations("last_seen", aa -> aa.max(m -> m.field("timestamp")))
                .aggregations("by_level", aa -> aa.terms(t -> t.field("level.keyword").size(2)))
                .aggregations("trend_6h", aa -> aa.dateHistogram(dh -> dh.field("timestamp").timeZone("+00:00").fixedInterval(fi -> fi.time("6h"))))),
            Void.class);
    }

    public SearchResponse<Void> aggregateIssueGroupsByEndpoint(Instant from, Instant to, String projectId, String service) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> {
                b.filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)));
                b.filter(f -> f.terms(t -> t.field("level.keyword")
                    .terms(tv -> tv.value(List.of(FieldValue.of("ERROR"), FieldValue.of("WARN"))))));
                b.filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))));
                b.filter(f -> f.exists(e -> e.field("endpoint")));
                b.mustNot(m -> m.term(t -> t.field("endpoint.keyword").value("")));
                if (StringUtils.hasText(service)) {
                    b.filter(f -> f.term(t -> t.field("service.keyword").value(service)));
                }
                return b;
            }))
            .aggregations("by_endpoint", a -> a.terms(t -> t.field("endpoint.keyword").size(1000))
                .aggregations("by_exception", aa -> aa.terms(t -> t.field("exception.keyword").missing("UnknownError").size(1000))
                    .aggregations("first_seen", aaa -> aaa.min(m -> m.field("timestamp")))
                    .aggregations("last_seen", aaa -> aaa.max(m -> m.field("timestamp")))
                    .aggregations("by_level", aaa -> aaa.terms(t -> t.field("level.keyword").size(8)))
                    .aggregations("by_service", aaa -> aaa.terms(t -> t.field("service.keyword").size(1000)))
                    .aggregations("trend_6h", aaa -> aaa.dateHistogram(dh -> dh.field("timestamp").timeZone("+00:00").fixedInterval(fi -> fi.time("6h")))))
                .aggregations("all_affected_services", aa -> aa.terms(t -> t.field("service.keyword").size(1000)))),
            Void.class);
    }

    public SearchResponse<Void> weeklyTrend(Instant from, Instant to, String projectId) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))))))
            .aggregations("by_day", a -> a.dateHistogram(dh -> dh
                    .field("timestamp")
                    .timeZone("+00:00")
                    .calendarInterval(CalendarInterval.Day))
                .aggregations("by_service", aa -> aa.terms(t -> t.field("service.keyword").size(1000)))
                .aggregations("error_count", aa -> aa.filter(f -> f.terms(t -> t.field("level.keyword")
                    .terms(tv -> tv.value(List.of(
                        FieldValue.of("ERROR"),
                        FieldValue.of("FATAL"),
                        FieldValue.of("CRITICAL")))))))),
            Void.class);
    }

    public SearchResponse<Void> ingestionRate(Instant from, Instant to, String projectId) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))))))
            .aggregations("by_hour", a -> a.dateHistogram(dh -> dh
                    .field("timestamp")
                    .timeZone("+00:00")
                    .fixedInterval(fi -> fi.time("1h")))
                .aggregations("by_level", aa -> aa.terms(t -> t.field("level.keyword").size(8)))),
            Void.class);
    }

    public SearchResponse<Void> severityMix(Instant from, Instant to, String projectId) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))))))
            .aggregations("by_day", a -> a.dateHistogram(dh -> dh
                    .field("timestamp")
                    .timeZone("+00:00")
                    .calendarInterval(CalendarInterval.Day))
                .aggregations("by_level", aa -> aa.terms(t -> t.field("level.keyword").size(8)))),
            Void.class);
    }

    public SearchResponse<Void> topEndpoints(Instant from, Instant to, String projectId, int limit) throws IOException {
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.terms(t -> t.field("level.keyword")
                    .terms(tv -> tv.value(List.of(
                        FieldValue.of("ERROR"),
                        FieldValue.of("FATAL"),
                        FieldValue.of("CRITICAL"))))))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(from.toString())
                    .lte(to.toString()))))
                .filter(f -> f.exists(e -> e.field("endpoint")))
                .mustNot(m -> m.term(t -> t.field("endpoint.keyword").value("")))))
            .aggregations("by_endpoint", a -> a.terms(t -> t.field("endpoint.keyword").size(limit))),
            Void.class);
    }

    public SearchResponse<Void> errorTriageTrend(Instant from, Instant to, String projectId) throws IOException {
        String histogramInterval = errorHistogramInterval(from, to);
        String rangeFromIso = from.toString();
        String rangeToIso = to.toString();
        FieldDateMath boundsMin = FieldDateMath.of(fdm -> fdm.expr(rangeFromIso));
        FieldDateMath boundsMax = FieldDateMath.of(fdm -> fdm.expr(rangeToIso));
        return client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))
                .filter(f -> f.terms(t -> t.field("level.keyword")
                    .terms(tv -> tv.value(List.of(
                        FieldValue.of("ERROR"),
                        FieldValue.of("FATAL"),
                        FieldValue.of("CRITICAL"))))))
                .filter(f -> f.range(r -> r.date(d -> d
                    .field("timestamp")
                    .gte(rangeFromIso)
                    .lte(rangeToIso))))))
            .aggregations("over_time", a -> a
                .dateHistogram(dh -> dh
                    .field("timestamp")
                    .timeZone("+00:00")
                    .fixedInterval(fi -> fi.time(histogramInterval))
                    .minDocCount(0)
                    .extendedBounds(eb -> eb.min(boundsMin).max(boundsMax)))
                .aggregations("open", aa -> aa.filter(f -> f.bool(b -> b
                    .mustNot(mn -> mn.term(t -> t.field("resolved").value(true))))))
                .aggregations("resolved", aa -> aa.filter(f -> f.term(t -> t.field("resolved").value(true))))),
            Void.class);
    }

    public List<String> totalServices(String projectId) throws IOException {
        SearchResponse<Void> response = client.search(s -> s
            .index(INDEX_PATTERN)
            .size(0)
            .query(q -> q.bool(b -> b
                .filter(f -> f.term(t -> t.field("projectId.keyword").value(projectId)))))
            .aggregations("by_service", a -> a.terms(t -> t.field("service.keyword").size(1000))),
            Void.class);

        if (response.aggregations() == null
            || response.aggregations().get("by_service") == null
            || response.aggregations().get("by_service").sterms() == null
            || response.aggregations().get("by_service").sterms().buckets() == null
            || response.aggregations().get("by_service").sterms().buckets().array() == null) {
            return Collections.emptyList();
        }

        return response.aggregations().get("by_service").sterms().buckets().array().stream()
            .map(StringTermsBucket::key)
            .filter(Objects::nonNull)
            .map(FieldValue::stringValue)
            .toList();
    }

    public long resolveLogs(List<String> logIds) throws IOException {
        if (logIds == null || logIds.isEmpty()) {
            return 0L;
        }
        UpdateByQueryResponse response = client.updateByQuery(u -> u
            .index(INDEX_PATTERN)
            .query(q -> q.ids(i -> i.values(logIds)))
            .script(s -> s.source("ctx._source.resolved = true; ctx._source.resolved_at = java.time.Instant.ofEpochMilli(ctx._now).toString();")));
        return response.updated() == null ? 0L : response.updated();
    }
}
