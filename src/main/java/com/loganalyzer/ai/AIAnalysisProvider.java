package com.loganalyzer.ai;

import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.List;

public interface AIAnalysisProvider {
    AIProvider provider();

    AnalysisResult analyze(List<LogDocument> logs, AIAnalysisRequest request);

    default AnalysisResult analyze(List<LogDocument> logs, AIAnalysisRequest request, AIStreamListener streamListener) {
        return analyze(logs, request);
    }
}
