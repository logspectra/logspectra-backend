package com.loganalyzer.domain.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedFixEmbeddable {
    private String fix;
    private String priority;
    private String effort;
    private String filePath;

    public static SuggestedFixEmbeddable fromModel(SuggestedFix model) {
        return new SuggestedFixEmbeddable(
            model.getFix(),
            model.getPriority(),
            model.getEffort(),
            model.getFilePath()
        );
    }

    public SuggestedFix toModel() {
        return SuggestedFix.builder()
            .fix(fix)
            .priority(priority)
            .effort(effort)
            .filePath(filePath)
            .build();
    }
}
