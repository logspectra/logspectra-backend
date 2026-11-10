package com.loganalyzer.ai;

import com.loganalyzer.ai.preprocessing.LogChunker;
import com.loganalyzer.ai.preprocessing.LogSummarizer;
import com.loganalyzer.ai.preprocessing.StackTraceNormalizer;
import com.loganalyzer.ai.prompt.PromptBuilder;
import com.loganalyzer.domain.model.AnalysisResult;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import com.loganalyzer.infrastructure.ratelimit.AIRateLimiter;
import com.loganalyzer.service.CacheService;
import com.logspectra.ai.LogSpectraAiFacade;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractChunkedAIAnalysisProvider implements AIAnalysisProvider {

    private static final String DEFAULT_PROJECT_ID = "default";

    private final PromptBuilder promptBuilder;
    private final LogChunker chunker;
    private final LogSummarizer summarizer;
    private final StackTraceNormalizer stackTraceNormalizer;
    private final CacheService cacheService;
    private final AIRateLimiter rateLimiter;
    private final AnalysisResultParser responseParser;
    private final AnalysisConfidenceScorer confidenceScorer;
    private final LogSpectraAiFacade logSpectraAiFacade;
    private final AiLogEventMapper aiLogEventMapper;

    protected AbstractChunkedAIAnalysisProvider(PromptBuilder promptBuilder,
                                                LogChunker chunker,
                                                LogSummarizer summarizer,
                                                StackTraceNormalizer stackTraceNormalizer,
                                                CacheService cacheService,
                                                AIRateLimiter rateLimiter,
                                                AnalysisResultParser responseParser,
                                                AnalysisConfidenceScorer confidenceScorer,
                                                LogSpectraAiFacade logSpectraAiFacade,
                                                AiLogEventMapper aiLogEventMapper) {
        this.promptBuilder = promptBuilder;
        this.chunker = chunker;
        this.summarizer = summarizer;
        this.stackTraceNormalizer = stackTraceNormalizer;
        this.cacheService = cacheService;
        this.rateLimiter = rateLimiter;
        this.responseParser = responseParser;
        this.confidenceScorer = confidenceScorer;
        this.logSpectraAiFacade = logSpectraAiFacade;
        this.aiLogEventMapper = aiLogEventMapper;
    }

    @Override
    public AnalysisResult analyze(List<LogDocument> logs, AIAnalysisRequest request) {
        return analyze(logs, request, AIStreamListener.NO_OP);
    }

    @Override
    public AnalysisResult analyze(List<LogDocument> logs, AIAnalysisRequest request, AIStreamListener streamListener) {
        List<LogDocument> normalized = logs.stream().map(this::normalize).toList();
        List<LogDocument> deduped = chunker.deduplicate(normalized);
        String fingerprint = computeFingerprint(logs);
        Optional<AnalysisResult> cached = cacheService.getAnalysis(DEFAULT_PROJECT_ID, provider() + ":" + fingerprint);
        if (cached.isPresent()) {
            AnalysisResult result = cached.get();
            result.setCachedResult(true);
            confidenceScorer.apply(deduped, result);
            return result;
        }

        rateLimiter.acquire(provider().name().toLowerCase() + "-analysis");
        List<List<LogDocument>> chunks = chunker.chunk(deduped);
        try {
            String basePrompt = chunks.size() == 1
                ? promptBuilder.buildAnalysisPrompt(chunks.getFirst(), provider().name().toLowerCase())
                : summarizeChunksThenAnalyze(chunks, request);
            String prompt = enrichWithCodeContext(basePrompt, deduped);
            String response = callProvider(getAnalysisModel(), prompt, request, streamListener, deduped);
            AnalysisResult result = responseParser.parse(response);
            confidenceScorer.apply(deduped, result);
            result.setFingerprint(fingerprint);
            result.setAnalyzedAt(Instant.now());
            String cacheKey = provider() + ":" + fingerprint;
            cacheService.storeAnalysis(DEFAULT_PROJECT_ID, cacheKey, result, Duration.ofHours(24));
            rateLimiter.markAnalyzed(cacheKey);
            return result;
        } catch (Exception ex) {
            throw new AIAnalysisException("Failed to analyze logs with " + provider() + ": " + ex.getMessage(), ex);
        }
    }

    private String enrichWithCodeContext(String basePrompt, List<LogDocument> logs) {
        if (logSpectraAiFacade == null || aiLogEventMapper == null || !logSpectraAiFacade.isCodeContextEnabled()) {
            return basePrompt;
        }
        String service = logs.stream()
            .map(LogDocument::getService)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse("unknown-service");
        return logSpectraAiFacade.enrichPrompt(basePrompt, service, aiLogEventMapper.toAiLogEvents(logs));
    }

    private LogDocument normalize(LogDocument log) {
        return LogDocument.builder()
            .id(log.getId())
            .projectId(log.getProjectId())
            .timestamp(log.getTimestamp())
            .level(log.getLevel())
            .service(log.getService())
            .traceId(log.getTraceId())
            .spanId(log.getSpanId())
            .thread(log.getThread())
            .logger(log.getLogger())
            .message(log.getMessage())
            .exception(log.getException())
            .stackTrace(stackTraceNormalizer.normalize(log.getStackTrace()))
            .host(log.getHost())
            .environment(log.getEnvironment())
            .build();
    }

    private String summarizeChunksThenAnalyze(List<List<LogDocument>> chunks, AIAnalysisRequest request) {
        List<String> summaries = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            List<LogDocument> chunk = chunks.get(i);
            String prompt = promptBuilder.buildChunkSummaryPrompt(formatChunk(chunk), i + 1, chunks.size());
            String aiSummary = callProvider(getSummaryModel(), prompt, request, AIStreamListener.NO_OP);
            summaries.add(aiSummary + "\nLocal summary: " + summarizer.summarize(chunk));
        }
        List<LogDocument> synthetic = List.of(LogDocument.builder()
            .timestamp(Instant.now())
            .level("SUMMARY")
            .service("summary")
            .message(String.join("\n--- CHUNK BOUNDARY ---\n", summaries))
            .build());
        return promptBuilder.buildAnalysisPrompt(synthetic, provider().name().toLowerCase() + "-multi-chunk-summary");
    }

    private String formatChunk(List<LogDocument> chunk) {
        return chunk.stream()
            .map(log -> String.format("[%s] %s [%s] %s", log.getTimestamp(), log.getLevel(), log.getService(), log.getMessage()))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    }

    private String computeFingerprint(List<LogDocument> logs) {
        String combined = logs.stream()
            .map(log -> log.getException() != null ? log.getException() : log.getMessage())
            .limit(10)
            .reduce((a, b) -> a + "|" + b)
            .orElse("empty");
        return DigestUtils.md5Hex(combined);
    }

    protected abstract String getAnalysisModel();

    protected abstract String getSummaryModel();

    protected abstract String callProvider(String model, String userPrompt, AIAnalysisRequest request);

    protected String callProvider(String model,
                                  String userPrompt,
                                  AIAnalysisRequest request,
                                  AIStreamListener streamListener) {
        return callProvider(model, userPrompt, request, streamListener, List.of());
    }

    /**
     * Hook for providers that need log context (e.g. iterative GitHub tool loop).
     * Default: same as {@link #callProvider(String, String, AIAnalysisRequest, AIStreamListener)}.
     */
    protected String callProvider(String model,
                                  String userPrompt,
                                  AIAnalysisRequest request,
                                  AIStreamListener streamListener,
                                  List<LogDocument> logs) {
        String response = callProvider(model, userPrompt, request);
        if (streamListener != null && streamListener.isEnabled() && StringUtils.hasText(response)) {
            streamListener.onToken(response);
        }
        return response;
    }
}
