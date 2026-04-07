package com.loganalyzer.domain.repository;

import com.loganalyzer.domain.model.IssueGroup;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IssueGroupRepository extends JpaRepository<IssueGroup, UUID> {
    Optional<IssueGroup> findByProjectIdAndFingerprint(UUID projectId, String fingerprint);
    List<IssueGroup> findByProjectIdAndResolvedFalseAndLastSeenAfterOrderByLastSeenDesc(UUID projectId, Instant lastSeenAfter, Pageable pageable);
    List<IssueGroup> findByProjectIdAndResolvedFalseAndServiceAndLastSeenAfterOrderByLastSeenDesc(UUID projectId, String service, Instant lastSeenAfter, Pageable pageable);

    @Query(value = """
        select extract(hour from resolved_at)::int as bucket_hour, count(*) as issue_count
        from issue_groups
        where project_id = :projectId
          and resolved = true
          and resolved_at is not null
          and resolved_at between :from and :to
        group by bucket_hour
        order by bucket_hour
        """, nativeQuery = true)
    List<Object[]> findResolvedIssueCountsByHour(UUID projectId, Instant from, Instant to);
}
