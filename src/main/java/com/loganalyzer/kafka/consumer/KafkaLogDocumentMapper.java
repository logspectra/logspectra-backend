package com.loganalyzer.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Normalizes Logstash / LogSpectra Kafka JSON into {@link LogDocument}.
 */
@Component
@RequiredArgsConstructor
public class KafkaLogDocumentMapper {

    private static final Pattern ROUTE_IN_MESSAGE = Pattern.compile("(?:^|\\s)route=([^\\s]+)");

    private final ObjectMapper objectMapper;

    public LogDocument fromKafkaPayload(String payload) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(payload);
        LogDocument document = objectMapper.treeToValue(root, LogDocument.class);

        if (document == null) {
            document = new LogDocument();
        }

        if (document.getTimestamp() == null) {
            document.setTimestamp(parseInstant(firstText(root, "@timestamp", "timestamp")));
        }

        if (!StringUtils.hasText(document.getLevel())) {
            document.setLevel(firstText(root, "level", "log.level"));
        }

        if (!StringUtils.hasText(document.getService())) {
            document.setService(firstText(root, "service"));
        }

        if (!StringUtils.hasText(document.getTraceId())) {
            document.setTraceId(resolveTraceId(root));
        }

        if (!StringUtils.hasText(document.getSpanId())) {
            document.setSpanId(firstText(root.path("mdc"), "spanId", "span_id"));
            if (!StringUtils.hasText(document.getSpanId())) {
                document.setSpanId(firstText(root, "spanId", "span_id"));
            }
        }

        if (!StringUtils.hasText(document.getThread())) {
            document.setThread(firstText(root, "thread", "thread_name"));
        }

        if (!StringUtils.hasText(document.getLogger())) {
            document.setLogger(firstText(root, "logger", "logger_name"));
        }

        if (!StringUtils.hasText(document.getMessage())) {
            document.setMessage(firstText(root, "message"));
        }

        if (!StringUtils.hasText(document.getStackTrace())) {
            String stack = firstText(root, "stackTrace", "stack_trace");
            if (StringUtils.hasText(stack)) {
                document.setStackTrace(stack);
            }
        }

        if (!StringUtils.hasText(document.getEndpoint())) {
            document.setEndpoint(firstText(root.path("mdc"), "endpoint"));
            if (!StringUtils.hasText(document.getEndpoint())) {
                document.setEndpoint(firstText(root, "endpoint"));
            }
            if (!StringUtils.hasText(document.getEndpoint())) {
                document.setEndpoint(extractRouteFromMessage(document.getMessage()));
            }
        }

        if (!StringUtils.hasText(document.getException())) {
            document.setException(firstText(root.path("mdc"), "exception"));
        }

        if (!StringUtils.hasText(document.getProjectId())) {
            document.setProjectId(firstText(root, "projectId", "project_id"));
        }

        if (!StringUtils.hasText(document.getHost())) {
            document.setHost(firstText(root, "host"));
        }

        if (!StringUtils.hasText(document.getEnvironment())) {
            document.setEnvironment(firstText(root, "environment"));
        }

        return document;
    }

    private String resolveTraceId(JsonNode root) {
        String fromRoot = firstText(root, "traceId", "trace_id", "correlationId", "correlation_id");
        if (StringUtils.hasText(fromRoot)) {
            return fromRoot;
        }
        JsonNode mdc = root.path("mdc");
        if (mdc.isObject()) {
            String fromMdc = firstText(mdc, "traceId", "trace_id");
            if (StringUtils.hasText(fromMdc)) {
                return fromMdc;
            }
        }
        return null;
    }

    private static Instant parseInstant(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String name : fieldNames) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull()) {
                if (child.isNull()) {
                    continue;
                }
                String text = child.asText().trim();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    static String extractRouteFromMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher matcher = ROUTE_IN_MESSAGE.matcher(message);
        if (matcher.find()) {
            String route = matcher.group(1).trim();
            return StringUtils.hasText(route) ? route : null;
        }
        return null;
    }
}
