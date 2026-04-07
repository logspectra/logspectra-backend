package com.loganalyzer.ai;

import com.loganalyzer.domain.model.AnalysisResult;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AIAnalysisExecution {
    AnalysisResult result;
    AIProvider providerUsed;
}
