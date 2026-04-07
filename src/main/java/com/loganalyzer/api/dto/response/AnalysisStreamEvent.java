package com.loganalyzer.api.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalysisStreamEvent {

    private String type;
    private String message;
    private Instant timestamp;
    private AnalysisResultResponse result;

    public static AnalysisStreamEvent progress(String message) {
        return AnalysisStreamEvent.builder()
            .type("progress")
            .message(message)
            .timestamp(Instant.now())
            .build();
    }

    public static AnalysisStreamEvent result(String message, AnalysisResultResponse result) {
        return AnalysisStreamEvent.builder()
            .type("result")
            .message(message)
            .timestamp(Instant.now())
            .result(result)
            .build();
    }

    public static AnalysisStreamEvent error(String message) {
        return AnalysisStreamEvent.builder()
            .type("error")
            .message(message)
            .timestamp(Instant.now())
            .build();
    }

    public static AnalysisStreamEvent complete(String message) {
        return AnalysisStreamEvent.builder()
            .type("complete")
            .message(message)
            .timestamp(Instant.now())
            .build();
    }
}

