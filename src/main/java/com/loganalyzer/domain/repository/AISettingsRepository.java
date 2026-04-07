package com.loganalyzer.domain.repository;

import com.loganalyzer.ai.AIProvider;
import com.loganalyzer.domain.model.AISettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AISettingsRepository extends JpaRepository<AISettings, UUID> {
    Optional<AISettings> findByIsActiveTrue();
    Optional<AISettings> findByProvider(AIProvider provider);
}
