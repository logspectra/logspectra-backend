package com.loganalyzer.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemediationResponse {
    private String fingerprint;
    private String service;
    private int patchesApplied;
    private String remediationPrUrl;
    private String remediationBranch;
    private Integer remediationPrNumber;
    private String message;
}
