package com.loganalyzer.infrastructure.exception;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.loganalyzer.api.dto.response.ErrorResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AIQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleAiQuota(AIQuotaExceededException exception) {
        log.warn("AI quota exceeded: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "60")
            .body(ErrorResponse.of("AI_QUOTA_EXCEEDED", exception.getMessage()));
    }

    @ExceptionHandler({ElasticsearchException.class, ElasticsearchServiceException.class})
    public ResponseEntity<ErrorResponse> handleElastic(Exception exception) {
        log.error("Elasticsearch operation failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.of("ES_ERROR", "Log storage temporarily unavailable"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", message, exception);
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", message));
    }


    @ExceptionHandler(InvalidProviderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidProvider(InvalidProviderException exception) {
        log.warn("Invalid AI provider request: {}", exception.getMessage(), exception);
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Malformed request payload", exception);
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request payload"));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException exception) {
        log.warn("Rate limit exceeded: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.of("RATE_LIMIT_EXCEEDED", exception.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        log.warn("Requested resource not found: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ProjectAccessDeniedException exception) {
        log.warn("Project access denied: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", exception.getMessage()));
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException exception) {
        log.warn("Authentication failed: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of("UNAUTHORIZED", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Access denied: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(AIAnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAiAnalysis(AIAnalysisException exception) {
        log.error("AI analysis request failed: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of("AI_ANALYSIS_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(AIProviderModelsFetchException.class)
    public ResponseEntity<ErrorResponse> handleAiProviderModelsFetch(AIProviderModelsFetchException exception) {
        log.error("AI provider model lookup failed for {}: {}", exception.getProvider(), exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse.of("AI_PROVIDER_MODELS_ERROR", exception.getMessage()));
    }


    @ExceptionHandler({IllegalArgumentException.class, NoActiveAISettingsException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException exception) {
        log.warn("Bad request exception: {}", exception.getMessage(), exception);
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of("INTERNAL_ERROR", exception.getMessage()));
    }
}
