package com.loganalyzer.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "issue_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueGroup {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 100)
    private String service;

    private String exceptionType;

    @Column(columnDefinition = "TEXT")
    private String errorSummary;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    private String severity;

    @Builder.Default
    private long occurrenceCount = 1L;

    @Column(nullable = false)
    private Instant firstSeen;

    @Column(nullable = false)
    private Instant lastSeen;

    private boolean resolved;

    private Instant resolvedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "issue_group_suggested_fixes", joinColumns = @JoinColumn(name = "issue_group_id"))
    @Builder.Default
    private List<SuggestedFixEmbeddable> suggestedFixEmbeddables = new ArrayList<>();

    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public List<SuggestedFix> getSuggestedFixes() {
        return suggestedFixEmbeddables.stream().map(SuggestedFixEmbeddable::toModel).toList();
    }

    public void setSuggestedFixes(List<SuggestedFix> fixes) {
        this.suggestedFixEmbeddables = fixes == null
            ? new ArrayList<>()
            : new ArrayList<>(fixes.stream().map(SuggestedFixEmbeddable::fromModel).toList());
    }
}
