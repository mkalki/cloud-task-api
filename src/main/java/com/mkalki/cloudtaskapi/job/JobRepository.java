package com.mkalki.cloudtaskapi.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    Optional<Job> findByIdAndOwnerId(UUID id, Long ownerId);

    Optional<Job> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    long countByOwnerIdAndStatusIn(Long ownerId, Collection<JobStatus> statuses);

    /**
     * Conditional status change: only updates the row if it is still in {@code from}.
     * Always bumps {@code version}, so a JPA entity loaded before this update can no longer
     * be saved over it. Returns the number of rows changed (0 or 1).
     *
     * <p>Call it through {@link JobStateMachine#transitionIf} only, which checks that the move
     * is legal. {@code clearAutomatically} detaches loaded jobs, so the next read sees the row
     * as it is in the database instead of a stale cached copy.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Job j
               set j.status = :to,
                   j.updatedAt = :now,
                   j.finishedAt = :finishedAt,
                   j.version = j.version + 1
             where j.id = :id
               and j.status = :from
            """)
    int updateStatusIfCurrent(
            @Param("id") UUID id,
            @Param("from") JobStatus from,
            @Param("to") JobStatus to,
            @Param("now") Instant now,
            @Param("finishedAt") Instant finishedAt);
}
