package com.mkalki.cloudtaskapi.job.dto;

import com.mkalki.cloudtaskapi.job.JobStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/jobs}. Same fields as {@link JobResponse} without the payload
 * and attempts: a 100-row page of 64 KB payloads would be about 6.4 MB. Use
 * {@code GET /api/v1/jobs/{id}} to read the payload.
 */
public record JobSummaryResponse(
        UUID id,
        String type,
        JobStatus status,
        int priority,
        int attempt,
        int maxAttempts,
        int timeoutSeconds,
        Instant runAt,
        UUID workflowId,
        String idempotencyKey,
        String lastError,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
