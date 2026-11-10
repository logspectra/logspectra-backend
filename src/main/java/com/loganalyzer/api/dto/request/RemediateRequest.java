package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemediateRequest {

    /** Issue fingerprint from the last Debug session (stored with suggested fixes). */
    @NotBlank
    private String fingerprint;

    /** Optional trace id for logging only. */
    private String traceId;
}
