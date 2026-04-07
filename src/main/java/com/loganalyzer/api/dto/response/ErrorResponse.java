package com.loganalyzer.api.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private Instant timestamp;

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder().code(code).message(message).timestamp(Instant.now()).build();
    }
}
