package com.loganalyzer.api.dto.response;

import com.loganalyzer.ai.AIProvider;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderModelsResponse {
    private AIProvider provider;
    private List<String> models;
}
