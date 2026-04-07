package com.loganalyzer.service;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.loganalyzer.api.dto.request.AggregationRequest;
import com.loganalyzer.api.dto.request.LogSearchRequest;
import com.loganalyzer.api.dto.response.AggregationResponse;
import com.loganalyzer.api.dto.response.LogSearchResponse;
import com.loganalyzer.api.dto.response.PagedResponse;
import com.loganalyzer.api.mapper.LogMapper;
import com.loganalyzer.domain.model.SeverityRule;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.elasticsearch.query.AggregationQueryBuilder;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import com.loganalyzer.infrastructure.exception.ElasticsearchServiceException;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogSearchService {

    private final LogElasticsearchRepository repository;
    private final AggregationQueryBuilder aggregationQueryBuilder;
    private final LogMapper logMapper;
    private final CacheService cacheService;
    private final SeverityRuleService severityRuleService;

    public PagedResponse<LogSearchResponse> search(LogSearchRequest request, String projectId) {
        return cacheService.<LogSearchResponse>getSearchResult(projectId, request).orElseGet(() -> doSearch(request, projectId));
    }

    private PagedResponse<LogSearchResponse> doSearch(LogSearchRequest request, String projectId) {
        try {
            SearchResponse<LogDocument> response = repository.search(request, projectId);
            List<SeverityRule> rules = severityRuleService.getRulesForResolution();
            List<LogSearchResponse> content = response.hits().hits().stream()
                .map(this::setIdIfPresent)
                .filter(java.util.Objects::nonNull)
                .map(document -> {
                    LogSearchResponse mapped = logMapper.toResponse(document);
                    mapped.setSeverity(severityRuleService.resolveSeverity(rules, document.getException(), document.getService(), document.getLevel()));
                    return mapped;
                })
                .toList();
            long total = response.hits().total() == null ? content.size() : response.hits().total().value();
            PagedResponse<LogSearchResponse> page = PagedResponse.<LogSearchResponse>builder()
                .content(content)
                .page(request.getPage())
                .size(request.getSize())
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / request.getSize()))
                .build();
            cacheService.storeSearchResult(projectId, request, page);
            return page;
        } catch (IOException ex) {
            throw new ElasticsearchServiceException("Failed to search logs", ex);
        }
    }

    public AggregationResponse aggregate(AggregationRequest request, String projectId) {
        try {
            return aggregationQueryBuilder.fromResponse(repository.aggregate(request, projectId));
        } catch (IOException ex) {
            throw new ElasticsearchServiceException("Failed to aggregate logs", ex);
        }
    }


    private LogDocument setIdIfPresent(Hit<LogDocument> hit) {
        LogDocument source = hit.source();
        if (source != null) {
            source.setId(hit.id());
        }
        return source;
    }
}
