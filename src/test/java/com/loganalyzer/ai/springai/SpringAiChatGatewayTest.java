package com.loganalyzer.ai.springai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loganalyzer.ai.AIAnalysisRequest;
import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.ai.AIStreamListener;
import org.junit.jupiter.api.Test;

class SpringAiChatGatewayTest {

    @Test
    void returnsEmptyWhenDisabled() {
        SpringAiProperties properties = new SpringAiProperties();
        properties.setEnabled(false);
        SpringAiChatGateway gateway = new SpringAiChatGateway(properties);

        assertFalse(gateway.tryComplete(
            AIProvider.OPENAI,
            "gpt-4",
            "system",
            "user",
            AIAnalysisRequest.builder().apiKey("sk-test").build(),
            AIStreamListener.NO_OP).isPresent());
    }

    @Test
    void geminiAlwaysUsesLegacyPath() {
        SpringAiProperties properties = new SpringAiProperties();
        properties.setEnabled(true);
        SpringAiChatGateway gateway = new SpringAiChatGateway(properties);

        assertFalse(gateway.tryComplete(
            AIProvider.GEMINI,
            "gemini-2.0-flash",
            "system",
            "user",
            AIAnalysisRequest.builder().apiKey("key").build(),
            AIStreamListener.NO_OP).isPresent());
    }

    @Test
    void isEnabledReflectsProperty() {
        SpringAiProperties properties = new SpringAiProperties();
        properties.setEnabled(true);
        assertTrue(new SpringAiChatGateway(properties).isEnabled());
    }
}
