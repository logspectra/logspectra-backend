package com.loganalyzer.ai.preprocessing;

import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LogChunker {

    private static final int MAX_TOKENS_PER_CHUNK = 3500;
    private static final int AVG_CHARS_PER_TOKEN = 4;
    private static final int MAX_CHARS = MAX_TOKENS_PER_CHUNK * AVG_CHARS_PER_TOKEN;

    public List<List<LogDocument>> chunk(List<LogDocument> logs) {
        List<List<LogDocument>> chunks = new ArrayList<>();
        List<LogDocument> current = new ArrayList<>();
        int currentSize = 0;
        for (LogDocument log : logs) {
            int logSize = estimateSize(log);
            if (currentSize + logSize > MAX_CHARS && !current.isEmpty()) {
                chunks.add(new ArrayList<>(current));
                current.clear();
                currentSize = 0;
            }
            current.add(log);
            currentSize += logSize;
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    public List<LogDocument> deduplicate(List<LogDocument> logs) {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, LogDocument> first = new LinkedHashMap<>();
        for (LogDocument log : logs) {
            String key = fingerprint(log);
            counts.merge(key, 1L, Long::sum);
            first.putIfAbsent(key, cloneLog(log));
        }
        return counts.entrySet().stream().map(entry -> {
            LogDocument doc = first.get(entry.getKey());
            if (entry.getValue() > 1) {
                doc.setMessage(doc.getMessage() + " [repeated x" + entry.getValue() + "]");
            }
            return doc;
        }).toList();
    }

    private String fingerprint(LogDocument log) {
        String msg = (log.getMessage() == null ? "" : log.getMessage())
            .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{uuid}")
            .replaceAll("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b", "{ip}")
            .replaceAll("\\d{10,}", "{id}");
        return log.getLevel() + "|" + log.getService() + "|" + msg;
    }

    private int estimateSize(LogDocument log) {
        return (log.getMessage() == null ? 0 : log.getMessage().length())
            + (log.getException() == null ? 0 : log.getException().length()) + 100;
    }

    private LogDocument cloneLog(LogDocument log) {
        return LogDocument.builder()
            .id(log.getId())
            .timestamp(log.getTimestamp())
            .level(log.getLevel())
            .service(log.getService())
            .traceId(log.getTraceId())
            .spanId(log.getSpanId())
            .thread(log.getThread())
            .logger(log.getLogger())
            .message(log.getMessage())
            .exception(log.getException())
            .stackTrace(log.getStackTrace())
            .host(log.getHost())
            .environment(log.getEnvironment())
            .build();
    }
}
