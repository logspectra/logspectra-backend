package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class LogSearchRequest {

    private String query;
    private String level;
    private String service;
    private String traceId;
    private String environment;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(200)
    private int size = 20;
}
