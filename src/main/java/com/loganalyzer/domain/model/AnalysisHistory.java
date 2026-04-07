package com.loganalyzer.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "ai_analysis_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false)
    private UUID projectId;

    private String modelUsed;

    private Integer tokensUsed;

    @Column(precision = 10, scale = 6)
    private BigDecimal costUsd;

    private Integer durationMs;

    private Boolean cachedResult;

    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (cachedResult == null) {
            cachedResult = false;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
