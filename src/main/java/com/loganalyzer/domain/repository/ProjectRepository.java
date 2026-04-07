package com.loganalyzer.domain.repository;

import com.loganalyzer.domain.model.Project;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
}
