package com.mkalki.cloudtaskapi.job;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    Optional<Job> findByIdAndOwnerId(UUID id, Long ownerId);

    Optional<Job> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    @Query("select j.status from Job j where j.id = :id")
    Optional<JobStatus> findStatusById(@Param("id") UUID id);

    /**
     * One page for the queue reconciler. The database orders by id, so the last id returned is
     * the cursor for the next page. Only id, priority and run_at are read, never the payload.
     */
    List<QueueEntry> findByStatusAndIdGreaterThanOrderByIdAsc(JobStatus status, UUID afterId, Limit limit);

    /**
     * Jobs that count toward the per-user cap ({@link JobStatus#activeStatuses()}).
     * The statuses are literals, not parameters, so PostgreSQL can use the partial index
     * idx_jobs_owner_active (V11). Keep the list in sync with JobStatus; a test checks it.
     */
    @Query(value = """
            select count(*) from jobs
             where owner_id = :ownerId
               and status in ('BLOCKED', 'PENDING', 'QUEUED', 'RUNNING')
            """, nativeQuery = true)
    long countActiveByOwnerId(@Param("ownerId") Long ownerId);

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

    /**
     * A worker's claim: QUEUED to RUNNING, one more attempt, who holds the job and until when.
     * Call it through {@link JobStateMachine#claim} only.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Job j
               set j.status = :to,
                   j.attempt = j.attempt + 1,
                   j.lockedBy = :workerId,
                   j.leaseExpiresAt = :leaseExpiresAt,
                   j.startedAt = :now,
                   j.updatedAt = :now,
                   j.finishedAt = null,
                   j.version = j.version + 1
             where j.id = :id
               and j.status = :from
            """)
    int claimIfQueued(
            @Param("id") UUID id,
            @Param("from") JobStatus from,
            @Param("to") JobStatus to,
            @Param("workerId") String workerId,
            @Param("now") Instant now,
            @Param("leaseExpiresAt") Instant leaseExpiresAt);

    /** Lease renewal. Changes a row only while this worker still holds this attempt. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Job j
               set j.leaseExpiresAt = :leaseExpiresAt,
                   j.version = j.version + 1
             where j.id = :id
               and j.status = :status
               and j.attempt = :attempt
               and j.lockedBy = :workerId
            """)
    int extendLeaseIfOwned(
            @Param("id") UUID id,
            @Param("status") JobStatus status,
            @Param("attempt") int attempt,
            @Param("workerId") String workerId,
            @Param("leaseExpiresAt") Instant leaseExpiresAt);

    /**
     * The fenced result of a run. Matches only while the row is in {@code from} for this attempt and
     * this worker. Call it through {@link JobStateMachine#transitionIfOwned} only.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Job j
               set j.status = :to,
                   j.updatedAt = :now,
                   j.finishedAt = :finishedAt,
                   j.lastError = :lastError,
                   j.leaseExpiresAt = null,
                   j.version = j.version + 1
             where j.id = :id
               and j.status = :from
               and j.attempt = :attempt
               and j.lockedBy = :workerId
            """)
    int updateStatusIfOwned(
            @Param("id") UUID id,
            @Param("from") JobStatus from,
            @Param("to") JobStatus to,
            @Param("attempt") int attempt,
            @Param("workerId") String workerId,
            @Param("lastError") String lastError,
            @Param("now") Instant now,
            @Param("finishedAt") Instant finishedAt);
}
