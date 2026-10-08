package com.loganalyzer.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class LiveLogStreamFanoutServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private LogStreamingService logStreamingService;

    private ObjectMapper objectMapper;
    private LiveLogStreamFanoutService fanoutService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        fanoutService = new LiveLogStreamFanoutService(stringRedisTemplate, objectMapper, logStreamingService);
    }

    @Test
    void fanout_deliversLocallyAndPublishesToRedis() {
        LogDocument doc = LogDocument.builder()
            .projectId("proj-1")
            .level("INFO")
            .message("hello")
            .timestamp(Instant.now())
            .build();

        fanoutService.fanout(doc);

        verify(logStreamingService).publish(doc);
        verify(stringRedisTemplate).convertAndSend(eq(LiveLogStreamFanoutService.REDIS_CHANNEL), anyString());
    }

    @Test
    void onRedisMessage_skipsEchoFromSamePublisher() {
        LogDocument doc = LogDocument.builder().projectId("p").level("INFO").message("x").build();
        fanoutService.fanout(doc);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(eq(LiveLogStreamFanoutService.REDIS_CHANNEL), captor.capture());

        reset(logStreamingService);
        fanoutService.onRedisMessage(captor.getValue());
        verify(logStreamingService, never()).publish(any());
    }

    @Test
    void onRedisMessage_deliversFromOtherPublisher() throws Exception {
        LogDocument doc = LogDocument.builder().projectId("p").level("INFO").message("remote").build();
        String payload = objectMapper.writeValueAsString(
            objectMapper.createObjectNode()
                .put("publisherId", "other-instance")
                .set("log", objectMapper.valueToTree(doc))
        );

        fanoutService.onRedisMessage(payload);
        verify(logStreamingService).publish(doc);
    }

    @Test
    void onRedisMessage_deliversLegacyPlainLogDocumentPayload() throws Exception {
        LogDocument doc = LogDocument.builder()
            .projectId("legacy")
            .level("WARN")
            .message("legacy")
            .build();

        fanoutService.onRedisMessage(objectMapper.writeValueAsString(doc));
        verify(logStreamingService).publish(doc);
    }
}
