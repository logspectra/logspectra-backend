package com.loganalyzer.ai.prompt;

import com.loganalyzer.elasticsearch.document.LogDocument;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildAnalysisPrompt(List<LogDocument> logs, String context) {
        String logBlock = formatLogs(logs);
        return String.format("""
            ## Log Analysis Task

            **Service Context:** %s
            **Time Window:** %s to %s
            **Total Log Lines:** %d

            ## Logs
            ```
            %s
            ```

            ## Required Output (JSON)
            {
              "errorSummary": "1-2 sentence summary of what went wrong",
              "rootCause": "precise technical root cause",
              "affectedServices": ["service1", "service2"],
              "errorPattern": "pattern or fingerprint of the error",
              "severity": "CRITICAL|HIGH|MEDIUM|LOW",
              "suggestedFixes": [
                { "fix": "description", "priority": "HIGH", "effort": "LOW" }
              ],
              "relatedTraceIds": ["traceId1"],
              "confidence": 0.0
            }
            """,
            context,
            logs.isEmpty() ? "N/A" : logs.get(0).getTimestamp(),
            logs.isEmpty() ? "N/A" : logs.get(logs.size() - 1).getTimestamp(),
            logs.size(),
            logBlock);
    }

    public String buildChunkSummaryPrompt(String chunk, int chunkIndex, int totalChunks) {
        return String.format("""
            Summarize the following log chunk (%d of %d) in 3-5 sentences.
            Focus on: errors, exceptions, failure patterns, affected services.
            Preserve exact exception class names and error messages.

            ```
            %s
            ```
            """, chunkIndex, totalChunks, chunk);
    }

    private String formatLogs(List<LogDocument> logs) {
        return logs.stream()
            .map(l -> String.format("[%s] %s [%s] %s%s",
                l.getTimestamp(), l.getLevel(), l.getService(), l.getMessage(),
                l.getException() != null ? " | EX: " + l.getException() : ""))
            .collect(Collectors.joining("\n"));
    }

    public String getSystemPrompt() {
        return PromptTemplate.SYSTEM_PROMPT;
    }
}
