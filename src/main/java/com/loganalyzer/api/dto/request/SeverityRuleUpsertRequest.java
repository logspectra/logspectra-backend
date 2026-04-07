package com.loganalyzer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SeverityRuleUpsertRequest {

    @NotBlank(message = "ruleName is required")
    @Size(max = 120, message = "ruleName must be at most 120 characters")
    private String ruleName;

    @Size(max = 255, message = "exceptionPattern must be at most 255 characters")
    private String exceptionPattern;

    @Size(max = 120, message = "serviceName must be at most 120 characters")
    private String serviceName;

    @NotBlank(message = "severity is required")
    @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW", message = "severity must be one of CRITICAL,HIGH,MEDIUM,LOW")
    private String severity;
}

