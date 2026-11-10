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
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogAnalysisStreamingService {

    private final LogAnalysisService logAnalysisService;

    @Value("${logspectra.analysis.sse-timeout-ms:${LOGSPECTRA_ANALYZE_SSE_TIMEOUT_MS:600000}}")
    private long sseTimeoutMs;

    @Resource(name = "aiExecutor")
    private Executor aiExecutor;

    public SseEmitter streamAnalysis(AnalyzeRequest request, UUID projectId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        AtomicBoolean clientOpen = new AtomicBoolean(true);

        emitter.onTimeout(() -> {
            clientOpen.set(false);
            log.warn("Analysis SSE timed out for project {} after {}ms (async work may still run)", projectId, sseTimeoutMs);
        });
        emitter.onCompletion(() -> clientOpen.set(false));
        emitter.onError(ex -> {
            clientOpen.set(false);
            log.warn("Analysis stream transport error for project {}", projectId, ex);
        });

        aiExecutor.execute(() -> runStream(request, projectId, emitter, clientOpen));
        return emitter;
    }

    private void runStream(AnalyzeRequest request, UUID projectId, SseEmitter emitter, AtomicBoolean clientOpen) {
        try {
            sendProgress(emitter, clientOpen, "Starting log analysis...");

            AnalysisResultResponse response =
                logAnalysisService.analyze(request, projectId, tokenListener(emitter, clientOpen)).join();

            if (!clientOpen.get()) {
                log.warn("Analysis completed for project {} but SSE was already closed", projectId);
                return;
            }
            sendEvent(emitter, clientOpen, "result", eventPayload("result", "Analysis complete", response));
            sendEvent(emitter, clientOpen, "complete", eventPayload("complete", "Analysis complete", null));
            completeQuietly(emitter, clientOpen);
        } catch (Exception ex) {
            log.error("Analysis stream failed for project {}", projectId, ex);
            Throwable root = ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null
                ? ex.getCause()
                : ex;
            String message = root.getMessage() != null ? root.getMessage() : "Unexpected streaming error";
            sendError(emitter, clientOpen, message);
            completeQuietly(emitter, clientOpen);
        }
    }

    private void sendProgress(SseEmitter emitter, AtomicBoolean clientOpen, String message) {
        sendEvent(emitter, clientOpen, "progress", eventPayload("progress", message, null));
    }

    private void sendError(SseEmitter emitter, AtomicBoolean clientOpen, String message) {
        sendEvent(emitter, clientOpen, "error", eventPayload("error", message, null));
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean clientOpen, String eventName, Object payload) {
        if (!clientOpen.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
        } catch (IllegalStateException ex) {
            clientOpen.set(false);
            log.debug("SSE already closed, skipping event {}", eventName);
        } catch (IOException ex) {
            clientOpen.set(false);
            log.debug("Unable to push {} event to client", eventName, ex);
        }
    }

    private void completeQuietly(SseEmitter emitter, AtomicBoolean clientOpen) {
        if (!clientOpen.get()) {
            return;
        }
        clientOpen.set(false);
        try {
            emitter.complete();
        } catch (IllegalStateException ex) {
            log.debug("SSE already completed");
        }
    }

    private AIStreamListener tokenListener(SseEmitter emitter, AtomicBoolean clientOpen) {
        return token -> sendEvent(emitter, clientOpen, "token", tokenPayload(token));
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

