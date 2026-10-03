package com.loganalyzer.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.elasticsearch.repository.LogElasticsearchRepository;
import com.loganalyzer.kafka.producer.AlertProducer;
import com.loganalyzer.service.LiveLogStreamFanoutService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogIngestionConsumer {

    private final ObjectMapper objectMapper;
    private final LogElasticsearchRepository repository;
    private final AlertProducer alertProducer;
    private final LiveLogStreamFanoutService liveLogStreamFanoutService;

    @KafkaListener(
        topics = "${kafka.topics.service-logs:service-logs}",
        groupId = "${kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, String>> records) {
        log.info("Received {} Kafka record(s) from topic service-logs", records.size());
        List<LogDocument> batch = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            String payload = record.value();
            if (!StringUtils.hasText(payload)) {
                log.warn("Skipping empty Kafka record topic={} partition={} offset={}", record.topic(), record.partition(), record.offset());
                continue;
            }

            try {
                LogDocument logDocument = objectMapper.readValue(payload, LogDocument.class);
                if (logDocument.getTimestamp() == null) {
                    logDocument.setTimestamp(java.time.Instant.now());
                }
                if (!StringUtils.hasText(logDocument.getProjectId())) {
                    logDocument.setProjectId("default");
                }
                batch.add(logDocument);
                log.info("Processing log record offset={} service={} level={}", record.offset(), logDocument.getService(), logDocument.getLevel());
                liveLogStreamFanoutService.fanout(logDocument);
                if ("ERROR".equalsIgnoreCase(logDocument.getLevel())) {
                    alertProducer.sendAlert(logDocument);
                }
            } catch (JsonProcessingException ex) {
                log.warn(
                    "Skipping malformed Kafka JSON topic={} partition={} offset={} reason={} payloadPreview={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    ex.getOriginalMessage(),
                    payloadPreview(payload)
                );
            } catch (Exception ex) {
                log.error("Failed to process Kafka record at offset {}", record.offset(), ex);
            }
        }
        if (batch.isEmpty()) {
            log.info("No valid log documents to index in this Kafka batch");
            return;
        }
        try {
            repository.bulkIndex(batch);
            log.info("Indexed {} log document(s) into Elasticsearch", batch.size());
        } catch (IOException ex) {
            log.error("Failed to bulk index logs to Elasticsearch", ex);
        }
    }

    private String payloadPreview(String payload) {
        if (!StringUtils.hasText(payload)) {
            return "<empty>";
        }
        String compact = payload.replace('\n', ' ').replace('\r', ' ').trim();
        int max = 200;
        if (compact.length() <= max) {
            return compact;
        }
        return compact.substring(0, max) + "...";
    }
}
