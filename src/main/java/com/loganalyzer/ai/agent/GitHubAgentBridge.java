package com.loganalyzer.ai.agent;

import com.loganalyzer.ai.AIAnalysisRequest;
import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.ai.AIStreamListener;
import com.loganalyzer.elasticsearch.document.LogDocument;
import com.loganalyzer.github.GitHubAppProperties;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Runs the iterative GitHub tool loop when App + registry preconditions are met, for any {@link AIProvider}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubAgentBridge {

    private final GitHubAgentSessionFactory sessionFactory;
    private final GitHubAgentLoop agentLoop;
    private final GitHubAppProperties properties;

    @Value("${ai.gemini.api-key:${GEMINI_API_KEY:${GOOGLE_API_KEY:}}}")
    private String defaultGeminiApiKey;

    public Optional<String> tryRun(AIProvider provider,
                                   String defaultModel,
                                   String userPrompt,
                                   AIAnalysisRequest request,
                                   List<LogDocument> logs,
                                   AIStreamListener streamListener,
                                   String defaultBaseUrl) {
        if (!properties.getApp().isEnabled() || !properties.getAgent().isToolCallingEnabled()) {
            return Optional.empty();
        }
        Optional<AgentSession> session = sessionFactory.create(logs);
        if (session.isEmpty()) {
            return Optional.empty();
        }

        String model = StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : defaultModel;
        String baseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : defaultBaseUrl;
        String apiKey = resolveApiKey(provider, request);

        if (provider != AIProvider.OLLAMA && !StringUtils.hasText(apiKey)) {
            log.debug("operation=agentLoop feature=github-agent status=skip provider={} reason=noApiKey", provider);
            return Optional.empty();
        }

        try {
            String response = agentLoop.run(provider, model, userPrompt, session.get(), baseUrl, apiKey);
            if (streamListener != null && streamListener.isEnabled() && StringUtils.hasText(response)) {
                streamListener.onToken(response);
            }
            return Optional.of(response);
        } catch (Exception ex) {
            log.warn("operation=agentLoop feature=github-agent status=error provider={} error={} fallback=oneShot",
                provider, ex.getMessage());
            return Optional.empty();
        }
    }

    private String resolveApiKey(AIProvider provider, AIAnalysisRequest request) {
        if (StringUtils.hasText(request.resolvedApiKey())) {
            return request.resolvedApiKey();
        }
        if (provider == AIProvider.GEMINI && StringUtils.hasText(defaultGeminiApiKey)) {
            return defaultGeminiApiKey;
        }
        return request.setting("apiKey");
    }
}
