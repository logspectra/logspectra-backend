package com.loganalyzer.ai;

import com.loganalyzer.elasticsearch.document.LogDocument;
import com.logspectra.ai.model.AiLogEvent;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiLogEventMapper {

    public AiLogEvent toAiLogEvent(LogDocument document) {
        if (document == null) {
            return null;
        }
        return AiLogEvent.builder()
            .id(document.getId())
            .projectId(document.getProjectId())
            .timestamp(document.getTimestamp())
            .level(document.getLevel())
            .service(document.getService())
            .traceId(document.getTraceId())
            .spanId(document.getSpanId())
            .thread(document.getThread())
            .logger(document.getLogger())
            .message(document.getMessage())
            .endpoint(document.getEndpoint())
            .exception(document.getException())
            .stackTrace(document.getStackTrace())
            .host(document.getHost())
            .environment(document.getEnvironment())
            .build();
    }

    public List<AiLogEvent> toAiLogEvents(List<LogDocument> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream().map(this::toAiLogEvent).toList();
    }
}
