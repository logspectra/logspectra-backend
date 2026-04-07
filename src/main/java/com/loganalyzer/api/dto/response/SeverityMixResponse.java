package com.loganalyzer.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeverityMixResponse {
    private List<SeverityMixPointResponse> points;
}
