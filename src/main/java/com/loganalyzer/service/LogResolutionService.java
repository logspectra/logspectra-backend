package com.loganalyzer.service;

import com.loganalyzer.api.dto.request.ResolveLogsRequest;
import com.loganalyzer.api.dto.response.ResolveLogsResponse;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import com.loganalyzer.infrastructure.exception.ElasticsearchServiceException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogResolutionService {

    private final LogElasticsearchRepository repository;

    public ResolveLogsResponse resolveLogs(ResolveLogsRequest request) {
        if (request == null || request.getLogIds() == null || request.getLogIds().isEmpty()) {
            log.warn("Attempted to resolve empty log list");
            return ResolveLogsResponse.builder()
                .resolvedCount(0L)
                .message("No logs to resolve")
                .build();
        }

        try {
            log.info("Resolving {} log(s)", request.getLogIds().size());
            long resolvedCount = repository.resolveLogs(request.getLogIds());
            log.info("Successfully resolved {} log(s)", resolvedCount);
            return ResolveLogsResponse.builder()
                .resolvedCount(resolvedCount)
                .message(String.format("Successfully resolved %d log(s)", resolvedCount))
                .build();
        } catch (IOException ex) {
            log.error("Failed to resolve logs", ex);
            throw new ElasticsearchServiceException("Failed to resolve logs", ex);
        }
    }
}



