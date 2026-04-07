package com.loganalyzer.ai.preprocessing;

import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LogSummarizer {

    public String summarize(List<LogDocument> logs) {
        long errors = logs.stream().filter(log -> "ERROR".equalsIgnoreCase(log.getLevel())).count();
        String services = logs.stream().map(LogDocument::getService).distinct().reduce((a, b) -> a + ", " + b).orElse("unknown");
        String exceptions = logs.stream().map(LogDocument::getException).filter(java.util.Objects::nonNull).distinct().reduce((a, b) -> a + ", " + b).orElse("none");
        return "Services=" + services + "; errors=" + errors + "; exceptions=" + exceptions;
    }
}
