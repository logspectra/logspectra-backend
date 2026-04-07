package com.loganalyzer.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.elasticsearch.document.LogDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.error-alerts:error-alerts}")
    private String alertsTopic;

    public void sendAlert(LogDocument logDocument) {
        try {
            kafkaTemplate.send(alertsTopic, logDocument.getService(), objectMapper.writeValueAsString(logDocument));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize error alert for log {}", logDocument.getId(), ex);
        }
    }
}
