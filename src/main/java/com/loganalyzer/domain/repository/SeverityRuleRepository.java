package com.loganalyzer.domain.repository;

import com.loganalyzer.domain.model.SeverityRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeverityRuleRepository extends JpaRepository<SeverityRule, UUID> {
}

