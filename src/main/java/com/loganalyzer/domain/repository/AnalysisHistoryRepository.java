package com.loganalyzer.domain.repository;

import com.loganalyzer.domain.model.AnalysisHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, UUID> {
    java.util.List<AnalysisHistory> findByProjectId(UUID projectId);

    java.util.List<AnalysisHistory> findByProjectIdAndCreatedAtBetween(UUID projectId, Instant from, Instant to);

    long countByProjectIdAndCreatedAtBetween(UUID projectId, Instant from, Instant to);

    long countByProjectIdAndCachedResultTrueAndCreatedAtBetween(UUID projectId, Instant from, Instant to);

    @Query("select coalesce(sum(a.costUsd), 0) from AnalysisHistory a where a.projectId = :projectId and a.createdAt between :from and :to")
    BigDecimal sumCostUsdByProjectIdAndCreatedAtBetween(UUID projectId, Instant from, Instant to);
}
