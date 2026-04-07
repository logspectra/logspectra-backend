package com.loganalyzer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.loganalyzer.api.dto.request.IssueQueryRequest;
import com.loganalyzer.api.dto.response.IssueGroupResponse;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueGroupingServiceTest {

    @Mock
    private LogElasticsearchRepository logElasticsearchRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private SeverityRuleService severityRuleService;

    @InjectMocks
    private IssueGroupingService issueGroupingService;

    @Test
    void getGroupedIssuesShouldReturnCachedValueWhenPresent() {
        UUID projectId = UUID.randomUUID();
        IssueQueryRequest request = new IssueQueryRequest();
        request.setWindow("1h");
        request.setGroupBy(IssueQueryRequest.GROUP_BY_SERVICE_EXCEPTION);
        request.setPage(0);
        request.setSize(20);

        List<IssueGroupResponse> cached = List.of(IssueGroupResponse.builder().id("x").build());
        String cacheKey = "1h:null:service_exception:0:20";
        when(cacheService.getIssues(projectId.toString(), cacheKey)).thenReturn(Optional.of(cached));

        List<IssueGroupResponse> result = issueGroupingService.getGroupedIssues(request, projectId);

        assertEquals(1, result.size());
        verifyNoInteractions(logElasticsearchRepository);
        verify(cacheService, never()).storeIssues(eq(projectId.toString()), eq(cacheKey), any());
    }

    @Test
    void getGroupedIssuesShouldQueryServiceExceptionAggregationOnCacheMiss() throws IOException {
        UUID projectId = UUID.randomUUID();
        IssueQueryRequest request = new IssueQueryRequest();
        request.setGroupBy(IssueQueryRequest.GROUP_BY_SERVICE_EXCEPTION);

        @SuppressWarnings("unchecked")
        SearchResponse<Void> response = (SearchResponse<Void>) org.mockito.Mockito.mock(SearchResponse.class);

        String cacheKey = "1h:null:service_exception:0:20";
        when(cacheService.getIssues(projectId.toString(), cacheKey)).thenReturn(Optional.empty());
        when(logElasticsearchRepository.aggregateIssueGroups(any(), any(), eq(projectId.toString()), isNull())).thenReturn(response);
        when(response.aggregations()).thenReturn(null);
        when(severityRuleService.getRulesForResolution()).thenReturn(List.of());

        List<IssueGroupResponse> result = issueGroupingService.getGroupedIssues(request, projectId);

        assertEquals(0, result.size());
        verify(logElasticsearchRepository).aggregateIssueGroups(any(), any(), eq(projectId.toString()), isNull());
        verify(cacheService).storeIssues(projectId.toString(), cacheKey, List.of());
    }

    @Test
    void getGroupedIssuesShouldQueryTraceAggregationWhenGroupByTraceId() throws IOException {
        UUID projectId = UUID.randomUUID();
        IssueQueryRequest request = new IssueQueryRequest();
        request.setGroupBy(IssueQueryRequest.GROUP_BY_TRACE_ID);

        @SuppressWarnings("unchecked")
        SearchResponse<Void> response = (SearchResponse<Void>) org.mockito.Mockito.mock(SearchResponse.class);

        String cacheKey = "1h:null:traceId:0:20";
        when(cacheService.getIssues(projectId.toString(), cacheKey)).thenReturn(Optional.empty());
        when(logElasticsearchRepository.aggregateIssueGroupsByTrace(any(), any(), eq(projectId.toString()), isNull())).thenReturn(response);
        when(response.aggregations()).thenReturn(null);
        when(severityRuleService.getRulesForResolution()).thenReturn(List.of());

        List<IssueGroupResponse> result = issueGroupingService.getGroupedIssues(request, projectId);

        assertEquals(0, result.size());
        verify(logElasticsearchRepository).aggregateIssueGroupsByTrace(any(), any(), eq(projectId.toString()), isNull());
        verify(cacheService).storeIssues(projectId.toString(), cacheKey, List.of());
    }
}

