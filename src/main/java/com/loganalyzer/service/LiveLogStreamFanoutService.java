package com.loganalyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.elasticsearch.document.LogDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveLogStreamFanoutService {

    public static final String REDIS_CHANNEL = "logspectra:live-logs";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final LogStreamingService logStreamingService;

    public void fanout(LogDocument logDocument) {
        if (logDocument == null) {
            return;
        }
        try {
            stringRedisTemplate.convertAndSend(REDIS_CHANNEL, objectMapper.writeValueAsString(logDocument));
        } catch (Exception ex) {
            log.warn("Redis live-log fanout failed; delivering to local SSE subscribers only", ex);
            deliverLocally(logDocument);
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
            LogDocument logDocument = objectMapper.readValue(payload, LogDocument.class);
            deliverLocally(logDocument);
        } catch (JsonProcessingException ex) {
            log.warn("Ignoring malformed live-log Redis payload", ex);
        }
    }
}
