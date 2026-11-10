package com.loganalyzer.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedFixEmbeddable {
    @Column(columnDefinition = "TEXT")
    private String fix;
    private String priority;
    private String effort;
    @Column(length = 512)
    private String filePath;
    @Column(columnDefinition = "TEXT")
    private String search;
    @Column(name = "replace", columnDefinition = "TEXT")
    private String replace;
    @Column(columnDefinition = "TEXT")
    private String newContent;

    public static SuggestedFixEmbeddable fromModel(SuggestedFix model) {
        SuggestedFixEmbeddable embeddable = new SuggestedFixEmbeddable();
        embeddable.setFix(model.getFix());
        embeddable.setPriority(model.getPriority());
        embeddable.setEffort(model.getEffort());
        embeddable.setFilePath(model.getFilePath());
        embeddable.setSearch(model.getSearch());
        embeddable.setReplace(model.getReplace());
        embeddable.setNewContent(model.getNewContent());
        return embeddable;
    }

    public SuggestedFix toModel() {
        return SuggestedFix.builder()
            .fix(fix)
            .priority(priority)
            .effort(effort)
            .filePath(filePath)
            .search(search)
            .replace(replace)
            .newContent(newContent)
            .build();
    }
}
