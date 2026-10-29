package com.loganalyzer.elasticsearch.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.loganalyzer.api.dto.request.AggregationRequest;
import com.loganalyzer.api.dto.request.LogSearchRequest;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.elasticsearch.query.LogQueryBuilder;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LogElasticsearchRepository {

    private static final String INDEX_PREFIX = "app-logs-";

    private final ElasticsearchClient client;
    private final LogQueryBuilder logQueryBuilder;

    public SearchResponse<LogDocument> search(LogSearchRequest request, String projectId) throws IOException {
        return logQueryBuilder.searchLogs(request, projectId);
    }

    public SearchResponse<Void> aggregate(AggregationRequest request, String projectId) throws IOException {
        return logQueryBuilder.aggregateErrors(request, projectId);
    }

    public List<LogDocument> fetchLogsForAnalysis(String traceId, String projectId) throws IOException {
        return logQueryBuilder.fetchLogsForAnalysis(traceId, projectId);
    }

    public SearchResponse<LogDocument> recentErrors(int minutes, String projectId) throws IOException {
        return logQueryBuilder.recentErrors(minutes, projectId);
    }

    public long countLogs(Instant from, Instant to, String projectId) throws IOException {
        return logQueryBuilder.countLogs(from, to, projectId);
    }

    public long countErrors(Instant from, Instant to, String projectId) throws IOException {
        return logQueryBuilder.countErrors(from, to, projectId);
    }

    public List<String> affectedErrorServices(Instant from, Instant to, String projectId, int limit) throws IOException {
        return logQueryBuilder.affectedErrorServices(from, to, projectId, limit);
    }

    public List<String> totalServices(String projectId) throws IOException {
        return logQueryBuilder.totalServices(projectId);
    }

    public SearchResponse<Void> aggregateIssueGroups(Instant from, Instant to, String projectId, String service) throws IOException {
        return logQueryBuilder.aggregateIssueGroups(from, to, projectId, service);
    }

    public SearchResponse<Void> aggregateIssueGroupsByTrace(Instant from, Instant to, String projectId, String service) throws IOException {
        return logQueryBuilder.aggregateIssueGroupsByTrace(from, to, projectId, service);
    }

    public SearchResponse<Void> aggregateIssueGroupsByEndpoint(Instant from, Instant to, String projectId, String service) throws IOException {
        return logQueryBuilder.aggregateIssueGroupsByEndpoint(from, to, projectId, service);
    }

    public SearchResponse<Void> weeklyTrend(Instant from, Instant to, String projectId) throws IOException {
        return logQueryBuilder.weeklyTrend(from, to, projectId);
    }

    public SearchResponse<Void> ingestionRate(Instant from, Instant to, String projectId) throws IOException {
        return logQueryBuilder.ingestionRate(from, to, projectId);
    }

    public SearchResponse<Void> severityMix(Instant from, Instant to, String projectId) throws IOException {
        return logQueryBuilder.severityMix(from, to, projectId);
    }

    public SearchResponse<Void> topEndpoints(Instant from, Instant to, String projectId, int limit) throws IOException {
        return logQueryBuilder.topEndpoints(from, to, projectId, limit);
    }

    public SearchResponse<Void> errorTriageTrend(Instant from, Instant to, String projectId) throws IOException {
        return logQueryBuilder.errorTriageTrend(from, to, projectId);
    }

    public long resolveLogs(List<String> logIds) throws IOException {
        return logQueryBuilder.resolveLogs(logIds);
    }

    public void bulkIndex(List<LogDocument> logs) throws IOException {
        if (logs.isEmpty()) {
            return;
        }
        BulkRequest.Builder builder = new BulkRequest.Builder();
        for (LogDocument log : logs) {
            String project = log.getProjectId() == null ? "default" : log.getProjectId().replaceAll("[^a-zA-Z0-9_-]", "_");
            String index = INDEX_PREFIX + project + "-" + log.getService() + "-" + DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC).format(log.getTimestamp());
            builder.operations(op -> op.index(idx -> idx.index(index).document(log)));
        }
        client.bulk(builder.build());
    }
}
