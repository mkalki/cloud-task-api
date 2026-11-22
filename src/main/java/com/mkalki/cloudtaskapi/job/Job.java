package com.mkalki.cloudtaskapi.job;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A unit of background work. Status changes go through {@link JobStateMachine} only:
 * {@link #setStatus} is package-private on purpose.
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    private Long ownerId;
    private UUID workflowId;
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int priority;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    private int attempt;
    private int maxAttempts;
    private int timeoutSeconds;
    private Instant runAt;
    private int pendingParents;
    private String lockedBy;
    private Instant leaseExpiresAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    private String lastError;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    /** Optimistic lock. Null until the row is first inserted. */
    @Version
    private Long version;

    protected Job() {
    }

    /** Creates a standalone job that is ready to be scheduled. */
    public static Job newPending(
            UUID id,
            Long ownerId,
            ValidatedJob request,
            String idempotencyKey,
            Instant now
    ) {
        Job job = new Job();
        job.id = id;
        job.ownerId = ownerId;
        job.type = request.type();
        job.payload = request.payload();
        job.priority = request.priority();
        job.status = JobStatus.PENDING;
        job.attempt = 0;
        job.maxAttempts = request.maxAttempts();
        job.timeoutSeconds = request.timeoutSeconds();
        job.runAt = request.runAt();
        job.pendingParents = 0;
        job.idempotencyKey = idempotencyKey;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public UUID getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public String getType() {
        return type;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public int getPriority() {
        return priority;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public int getPendingParents() {
        return pendingParents;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    void setStatus(JobStatus status) {
        this.status = status;
    }

    void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
