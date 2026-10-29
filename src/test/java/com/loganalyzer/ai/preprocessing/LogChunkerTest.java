package com.loganalyzer.ai.preprocessing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.loganalyzer.elasticsearch.document.LogDocument;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogChunkerTest {

    private final LogChunker chunker = new LogChunker();

    @Test
    void deduplicate_retainsProjectId() {
        LogDocument log = LogDocument.builder()
            .projectId("proj-abc")
            .timestamp(Instant.now())
            .level("ERROR")
            .service("gateway-service")
            .message("boom")
            .build();

        List<LogDocument> out = chunker.deduplicate(List.of(log, log));

        assertEquals(1, out.size());
        assertEquals("proj-abc", out.getFirst().getProjectId());
    }
}
