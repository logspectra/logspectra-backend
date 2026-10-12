package com.loganalyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveLogStreamFanoutService {

    public static final String REDIS_CHANNEL = "logspectra:live-logs";

    private final String instanceId = UUID.randomUUID().toString();
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final LogStreamingService logStreamingService;

    public void fanout(LogDocument logDocument) {
        if (logDocument == null) {
            return;
        }
        deliverLocally(logDocument);
        try {
            FanoutEnvelope envelope = new FanoutEnvelope(instanceId, logDocument);
            stringRedisTemplate.convertAndSend(REDIS_CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (Exception ex) {
            log.warn("Redis live-log fanout failed; local SSE subscribers already notified", ex);
        }
    }

    void deliverLocally(LogDocument logDocument) {
        logStreamingService.publish(logDocument);
        if ("ERROR".equalsIgnoreCase(logDocument.getLevel())) {
            logStreamingService.publishAlert(logDocument);
        }
    }

    public void onRedisMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.has("publisherId") && root.has("log")) {
                String publisherId = root.get("publisherId").asText();
                if (instanceId.equals(publisherId)) {
                    return;
                }
                LogDocument logDocument = objectMapper.treeToValue(root.get("log"), LogDocument.class);
                deliverLocally(logDocument);
                return;
            }
            LogDocument logDocument = objectMapper.readValue(payload, LogDocument.class);
            deliverLocally(logDocument);
        } catch (JsonProcessingException ex) {
            log.warn("Ignoring malformed live-log Redis payload", ex);
        }
    }

    private record FanoutEnvelope(String publisherId, LogDocument log) {}
}
