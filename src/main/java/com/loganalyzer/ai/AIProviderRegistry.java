package com.loganalyzer.ai;

import com.loganalyzer.infrastructure.exception.InvalidProviderException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class AIProviderRegistry {

    private final Map<AIProvider, AIAnalysisProvider> providers;

    public AIProviderRegistry(java.util.List<AIAnalysisProvider> providerStrategies) {
        this.providers = providerStrategies.stream()
            .collect(java.util.stream.Collectors.toMap(AIAnalysisProvider::provider, Function.identity()));
    }

    public AIAnalysisProvider get(AIProvider provider) {
        AIAnalysisProvider strategy = providers.get(provider);
        if (strategy == null) {
            throw new InvalidProviderException("Provider not configured: " + provider);
        }
        return strategy;
    }

    public AIProvider resolve(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        try {
            return AIProvider.from(provider);
        } catch (IllegalArgumentException ex) {
            throw new InvalidProviderException("Invalid provider '" + provider + "'. Supported providers: " + Arrays.toString(AIProvider.values()));
        }
    }
}
