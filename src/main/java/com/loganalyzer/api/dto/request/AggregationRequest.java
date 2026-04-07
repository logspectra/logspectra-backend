package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class AggregationRequest {
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;
}
