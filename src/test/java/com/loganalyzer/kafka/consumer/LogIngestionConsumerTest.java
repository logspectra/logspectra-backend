package com.loganalyzer.kafka.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import com.loganalyzer.kafka.producer.AlertProducer;
import com.loganalyzer.service.LiveLogStreamFanoutService;

import java.io.IOException;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogIngestionConsumerTest {

    @Mock
    private LogElasticsearchRepository repository;

    @Mock
    private AlertProducer alertProducer;

    @Mock
    private LiveLogStreamFanoutService liveLogStreamFanoutService;

    private LogIngestionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LogIngestionConsumer(new ObjectMapper(), repository, alertProducer, liveLogStreamFanoutService);
    }

    @Test
    void consumeShouldSkipBlankPayloadAndNotIndex() throws IOException {
        ConsumerRecord<String, String> blank = new ConsumerRecord<>("service-logs", 0, 10L, null, "   ");

        consumer.consume(List.of(blank));

        verify(repository, never()).bulkIndex(any());
        verify(alertProducer, never()).sendAlert(any());
    }

    @Test
    void consumeShouldSkipMalformedJsonAndNotIndex() throws IOException {
        ConsumerRecord<String, String> malformed = new ConsumerRecord<>("service-logs", 0, 11L, null,
            "{\"projectId\":\"bb8847f3-e92d-401f-ace7-b29ffbfa5e00\",\"environment\":\"prod");

        consumer.consume(List.of(malformed));

        verify(repository, never()).bulkIndex(any());
        verify(alertProducer, never()).sendAlert(any());
    }

    @Test
    void consumeShouldProcessValidMessageAndSetDefaultProjectWhenMissing() throws Exception {
        ConsumerRecord<String, String> valid = new ConsumerRecord<>("service-logs", 0, 12L, null,
            "{\"level\":\"INFO\",\"service\":\"auth-service\",\"message\":\"ok\"}");

        consumer.consume(List.of(valid));

        ArgumentCaptor<List<LogDocument>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).bulkIndex(batchCaptor.capture());
        List<LogDocument> indexed = batchCaptor.getValue();
        assertEquals(1, indexed.size());
        assertEquals("default", indexed.getFirst().getProjectId());
        assertEquals("INFO", indexed.getFirst().getLevel());

        verify(liveLogStreamFanoutService).fanout(any(LogDocument.class));
        verify(alertProducer, never()).sendAlert(any());
    }

    @Test
    void consumeShouldPublishAlertForErrorLevel() throws Exception {
        ConsumerRecord<String, String> errorRecord = new ConsumerRecord<>("service-logs", 0, 13L, null,
            "{\"projectId\":\"bb8847f3-e92d-401f-ace7-b29ffbfa5e00\",\"level\":\"ERROR\",\"service\":\"auth-service\",\"message\":\"boom\"}");

        consumer.consume(List.of(errorRecord));

        verify(liveLogStreamFanoutService).fanout(any(LogDocument.class));
        verify(alertProducer).sendAlert(any(LogDocument.class));
        verify(repository).bulkIndex(any());
    }
}
