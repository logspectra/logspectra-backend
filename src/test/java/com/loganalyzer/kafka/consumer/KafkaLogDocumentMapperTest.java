package com.loganalyzer.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loganalyzer.elasticsearch.document.LogDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaLogDocumentMapperTest {

    private KafkaLogDocumentMapper mapper;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mapper = new KafkaLogDocumentMapper(objectMapper);
    }

    @Test
    void readsTraceIdFromMdcObject() throws Exception {
        String json = """
            {
              "timestamp": "2026-01-01T12:00:00Z",
              "level": "INFO",
              "service": "gateway-service",
              "message": "hello",
              "mdc": { "traceId": "abc-trace-123" }
            }
            """;

        LogDocument doc = mapper.fromKafkaPayload(json);

        assertThat(doc.getTraceId()).isEqualTo("abc-trace-123");
    }

    @Test
    void readsTopLevelTraceIdAndLogstashAliases() throws Exception {
        String json = """
            {
              "@timestamp": "2026-01-01T12:00:00Z",
              "level": "ERROR",
              "service": "orders",
              "traceId": "top-level-trace",
              "logger_name": "com.example.Orders",
              "thread_name": "main",
              "stack_trace": "java.lang.RuntimeException: boom"
            }
            """;

        LogDocument doc = mapper.fromKafkaPayload(json);

        assertThat(doc.getTraceId()).isEqualTo("top-level-trace");
        assertThat(doc.getLogger()).isEqualTo("com.example.Orders");
        assertThat(doc.getThread()).isEqualTo("main");
        assertThat(doc.getStackTrace()).contains("RuntimeException");
    @Test
    void readsEndpointFromMdcAndLegacyRouteInMessage() throws Exception {
        String json = """
            {
              "timestamp": "2026-01-01T12:00:00Z",
              "level": "ERROR",
              "service": "gateway-service",
              "message": "upstream failure route=/api/checkout status=502",
              "endpoint": "/api/checkout"
            }
            """;

        LogDocument doc = mapper.fromKafkaPayload(json);

        assertThat(doc.getEndpoint()).isEqualTo("/api/checkout");
    }

    @Test
    void derivesEndpointFromRouteTokenInMessage() throws Exception {
        assertThat(KafkaLogDocumentMapper.extractRouteFromMessage("fail route=/api/catalog batch=1/3"))
            .isEqualTo("/api/catalog");
    }
}
