package com.loganalyzer.domain.repository;

import com.loganalyzer.domain.model.UserProject;
import com.loganalyzer.domain.model.UserProjectId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectRepository extends JpaRepository<UserProject, UserProjectId> {
    boolean existsByIdUserIdAndIdProjectId(UUID userId, UUID projectId);
    List<UserProject> findAllByIdUserId(UUID userId);
    void deleteAllByIdProjectId(UUID projectId);
}
