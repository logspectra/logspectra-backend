package com.loganalyzer.service;

import com.loganalyzer.elasticsearch.document.LogDocument;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LogStreamingService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String projectId, String service, String level) {
        String key = normalize(projectId) + ":" + normalize(service) + ":" + normalize(level) + ":" + java.util.UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(key, emitter);
        emitter.onCompletion(() -> emitters.remove(key));
        emitter.onTimeout(() -> emitters.remove(key));
        return emitter;
    }

    public void publish(LogDocument logDocument) {
        emitters.entrySet().removeIf(entry -> {
            try {
                String[] parts = entry.getKey().split(":", 4);
                String projectFilter = parts[0];
                String serviceFilter = parts[1];
                String levelFilter = parts[2];
                if (("*".equals(projectFilter) || projectFilter.equalsIgnoreCase(normalize(logDocument.getProjectId())))
                    && ("*".equals(serviceFilter) || serviceFilter.equalsIgnoreCase(normalize(logDocument.getService())))
                    && ("*".equals(levelFilter) || levelFilter.equalsIgnoreCase(normalize(logDocument.getLevel())))) {
                    entry.getValue().send(SseEmitter.event().name("log").data(logDocument, MediaType.APPLICATION_JSON));
                }
                return false;
            } catch (IOException ex) {
                return true;
            }
        });
    }

    private String normalize(String input) {
        return StringUtils.hasText(input) ? input.trim() : "*";
    }
}
