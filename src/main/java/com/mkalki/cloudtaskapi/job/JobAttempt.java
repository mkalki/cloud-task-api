package com.mkalki.cloudtaskapi.job;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One execution attempt of a job. Workers write these from F3; F1 only reads them. */
@Entity
@Table(name = "job_attempts")
public class JobAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID jobId;
    private int attemptNo;
    private String workerId;
    private Instant startedAt;
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    private JobAttemptOutcome outcome;

    private String error;
    private Long durationMs;

    protected JobAttempt() {
    }

    public JobAttempt(
            UUID jobId,
            int attemptNo,
            String workerId,
            Instant startedAt,
            Instant finishedAt,
            JobAttemptOutcome outcome,
            String error,
            Long durationMs
    ) {
        this.jobId = jobId;
        this.attemptNo = attemptNo;
        this.workerId = workerId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.outcome = outcome;
        this.error = error;
        this.durationMs = durationMs;
    }

    public Long getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getWorkerId() {
        return workerId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public JobAttemptOutcome getOutcome() {
        return outcome;
    }

    public String getError() {
        return error;
    }

    public Long getDurationMs() {
        return durationMs;
    }
}
