package com.loganalyzer.service;

import com.loganalyzer.ai.AIStreamListener;
import com.loganalyzer.api.dto.request.AnalyzeRequest;
import com.loganalyzer.api.dto.response.AnalysisResultResponse;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogAnalysisStreamingService {

    private final LogAnalysisService logAnalysisService;

    @Resource(name = "aiExecutor")
    private Executor aiExecutor;

    public void streamAnalysis(AnalyzeRequest request, UUID projectId, SseEmitter emitter) {
        emitter.onTimeout(() -> {
            sendError(emitter, "Analysis stream timed out");
            emitter.complete();
        });
        emitter.onError(ex -> log.warn("Analysis stream transport error for project {}", projectId, ex));

        aiExecutor.execute(() -> runStream(request, projectId, emitter));
    }

    private void runStream(AnalyzeRequest request, UUID projectId, SseEmitter emitter) {
        try {
            sendProgress(emitter, "Starting log analysis...");

            AnalysisResultResponse response = logAnalysisService.analyze(request, projectId, tokenListener(emitter)).join();

            sendEvent(emitter, "result", eventPayload("result", "Analysis complete", response));
            sendEvent(emitter, "complete", eventPayload("complete", "Analysis complete", null));
            emitter.complete();
        } catch (Exception ex) {
            log.error("Analysis stream failed for project {}", projectId, ex);
            sendError(emitter, ex.getMessage() != null ? ex.getMessage() : "Unexpected streaming error");
            emitter.complete();
        }
    }

    private void sendProgress(SseEmitter emitter, String message) throws IOException {
        sendEvent(emitter, "progress", eventPayload("progress", message, null));
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            sendEvent(emitter, "error", eventPayload("error", message, null));
        } catch (IOException ioException) {
            log.debug("Unable to push error event to client", ioException);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
    }

    private AIStreamListener tokenListener(SseEmitter emitter) {
        return token -> {
            try {
                sendEvent(emitter, "token", tokenPayload(token));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to forward token chunk to stream", ex);
            }
        };
    }

    private Map<String, Object> tokenPayload(String token) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "token");
        payload.put("text", token);
        payload.put("timestamp", Instant.now());
        return payload;
    }

    private Map<String, Object> eventPayload(String type, String message, AnalysisResultResponse result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("message", message);
        payload.put("timestamp", Instant.now());
        if (result != null) {
            payload.put("result", result);
        }
        return payload;
    }

}

