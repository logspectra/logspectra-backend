package com.loganalyzer.ai.springai;

import com.loganalyzer.ai.AIAnalysisRequest;
import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.ai.AIStreamListener;
import com.loganalyzer.infrastructure.exception.AIAnalysisException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpringAiChatGateway {

    private static final String DEFAULT_OPENAI_BASE = "https://api.openai.com";
    private static final String DEFAULT_CLAUDE_BASE = "https://api.anthropic.com";
    private static final String DEFAULT_OLLAMA_BASE = "http://localhost:11434";
    private static final String DEFAULT_CLAUDE_API_VERSION = "2023-06-01";

    private final SpringAiProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Runs a chat completion via Spring AI when enabled for the provider.
     * Returns empty to signal the caller should use the legacy HTTP client (Gemini always uses legacy today).
     */
    public Optional<String> tryComplete(AIProvider provider,
                                        String defaultModel,
                                        String systemPrompt,
                                        String userPrompt,
                                        AIAnalysisRequest request,
                                        AIStreamListener streamListener) {
        if (!properties.isEnabled() || provider == AIProvider.GEMINI) {
            return Optional.empty();
        }
        try {
            String model = StringUtils.hasText(request.resolvedModel()) ? request.resolvedModel() : defaultModel;
            ChatModel chatModel = createChatModel(provider, model, request);
            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));

            if (streamListener != null && streamListener.isEnabled()) {
                String streamed = streamWithTokens(chatModel, prompt, streamListener);
                if (!StringUtils.hasText(streamed)) {
                    throw new AIAnalysisException(provider + " (Spring AI) stream returned empty content");
                }
                return Optional.of(streamed);
            }

            ChatResponse response = chatModel.call(prompt);
            String text = response.getResult().getOutput().getText();
            if (!StringUtils.hasText(text)) {
                throw new AIAnalysisException(provider + " (Spring AI) returned empty content");
            }
            return Optional.of(text);
        } catch (Exception ex) {
            log.warn("Spring AI call failed for {}, falling back to legacy HTTP: {}", provider, ex.getMessage());
            return Optional.empty();
        }
    }

    private String streamWithTokens(ChatModel chatModel, Prompt prompt, AIStreamListener listener) {
        StringBuilder accumulated = new StringBuilder();
        Flux<ChatResponse> flux = chatModel.stream(prompt);
        flux.doOnNext(chunk -> {
            if (chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                return;
            }
            String delta = chunk.getResult().getOutput().getText();
            if (StringUtils.hasText(delta)) {
                listener.onToken(delta);
                accumulated.append(delta);
            }
        }).blockLast();
        return accumulated.toString();
    }

    private ChatModel createChatModel(AIProvider provider, String model, AIAnalysisRequest request) {
        return switch (provider) {
            case OPENAI -> createOpenAi(model, request);
            case CLAUDE -> createAnthropic(model, request);
            case OLLAMA -> createOllama(model, request);
            default -> throw new IllegalArgumentException("Unsupported provider for Spring AI: " + provider);
        };
    }

    private ChatModel createOpenAi(String model, AIAnalysisRequest request) {
        String apiKey = request.resolvedApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new AIAnalysisException("OPENAI requires apiKey");
        }
        String baseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_OPENAI_BASE;
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.1)
            .maxTokens(1500)
            .responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null))
            .build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    private ChatModel createAnthropic(String model, AIAnalysisRequest request) {
        String apiKey = request.resolvedApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new AIAnalysisException("CLAUDE requires apiKey");
        }
        String baseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_CLAUDE_BASE;
        String apiVersion = StringUtils.hasText(request.setting("apiVersion"))
            ? request.setting("apiVersion")
            : DEFAULT_CLAUDE_API_VERSION;
        AnthropicApi api = AnthropicApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .anthropicVersion(apiVersion)
            .build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
            .model(model)
            .temperature(0.1)
            .maxTokens(1500)
            .build();
        return AnthropicChatModel.builder().anthropicApi(api).defaultOptions(options).build();
    }

    private ChatModel createOllama(String model, AIAnalysisRequest request) {
        String baseUrl = StringUtils.hasText(request.resolvedBaseUrl()) ? request.resolvedBaseUrl() : DEFAULT_OLLAMA_BASE;
        OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
        OllamaOptions options = OllamaOptions.builder()
            .model(model)
            .temperature(0.1)
            .build();
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }
}
