package com.loganalyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
@RequiredArgsConstructor
public class LogStreamingService {

    private final ObjectMapper objectMapper;
    private final Map<String, StreamSubscription> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String projectId, String service, String level) {
        String subscriptionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(-1L);
        StreamSubscription subscription = new StreamSubscription(
            subscriptionId,
            normalize(projectId),
            normalize(service),
            normalize(level),
            emitter
        );
        subscriptions.put(subscriptionId, subscription);

        emitter.onCompletion(() -> subscriptions.remove(subscriptionId));
        emitter.onTimeout(() -> subscriptions.remove(subscriptionId));
        emitter.onError(ex -> subscriptions.remove(subscriptionId));

        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"transport\":\"sse\"}", MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            subscriptions.remove(subscriptionId);
            emitter.completeWithError(ex);
        }
        log.debug("SSE subscription opened id={} project={} service={} level={} active={}",
            subscriptionId, subscription.projectFilter(), subscription.serviceFilter(), subscription.levelFilter(), subscriptions.size());
        return emitter;
    }

    public void publish(LogDocument logDocument) {
        broadcast("log", logDocument, false);
    }

    public void publishAlert(LogDocument logDocument) {
        broadcast("alert", logDocument, true);
    }

    private void broadcast(String eventName, LogDocument logDocument, boolean errorsOnly) {
        if (logDocument == null) {
            return;
        }
        if (errorsOnly && !"ERROR".equalsIgnoreCase(logDocument.getLevel())) {
            return;
        }
        if (!StringUtils.hasText(logDocument.getId())) {
            logDocument.setId(UUID.randomUUID().toString());
        }

        int delivered = 0;
        for (Map.Entry<String, StreamSubscription> entry : subscriptions.entrySet()) {
            StreamSubscription subscription = entry.getValue();
            if (!subscription.matches(logDocument)) {
                continue;
            }
            try {
                String payload = objectMapper.writeValueAsString(logDocument);
                subscription.emitter().send(SseEmitter.event().name(eventName).data(payload));
                delivered++;
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize log document for SSE event={}", eventName, ex);
            } catch (IOException ex) {
                log.debug("Removing stale SSE subscription {}", subscription.id(), ex);
                subscriptions.remove(entry.getKey());
            } catch (Exception ex) {
                log.warn("Unexpected SSE publish failure subscription={}", subscription.id(), ex);
                subscriptions.remove(entry.getKey());
            }
        }

        if (delivered == 0 && !subscriptions.isEmpty()) {
            log.warn(
                "SSE publish had subscribers but none matched event={} projectId={} service={} level={} activeSubscriptions={}",
                eventName,
                logDocument.getProjectId(),
                logDocument.getService(),
                logDocument.getLevel(),
                subscriptions.size()
            );
        } else if (delivered == 0) {
            log.debug(
                "SSE publish with no active subscribers event={} projectId={}",
                eventName,
                logDocument.getProjectId()
            );
        }
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        subscriptions.entrySet().removeIf(entry -> {
            try {
                entry.getValue().emitter().send(SseEmitter.event().comment("keepalive"));
                return false;
            } catch (IOException ex) {
                return true;
            }
        });
    }

    private String normalize(String input) {
        return StringUtils.hasText(input) ? input.trim() : "*";
    }

    private record StreamSubscription(
        String id,
        String projectFilter,
        String serviceFilter,
        String levelFilter,
        SseEmitter emitter
    ) {
        boolean matches(LogDocument document) {
            String projectId = document.getProjectId() == null ? "" : document.getProjectId().trim();
            String service = document.getService() == null ? "" : document.getService().trim();
            String level = document.getLevel() == null ? "" : document.getLevel().trim();

            return ("*".equals(projectFilter) || projectFilter.equalsIgnoreCase(projectId))
                && ("*".equals(serviceFilter) || serviceFilter.equalsIgnoreCase(service))
                && ("*".equals(levelFilter) || levelFilter.equalsIgnoreCase(level));
        }
    }
}
