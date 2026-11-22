package com.mkalki.cloudtaskapi.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.mkalki.cloudtaskapi.job.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String type,
        JobStatus status,
        int priority,
        int attempt,
        int maxAttempts,
        int timeoutSeconds,
        Instant runAt,
        JsonNode payload,
        UUID workflowId,
        String idempotencyKey,
        String lastError,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt,
        /* Only present on GET /jobs/{id}. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<JobAttemptResponse> attempts
) {
}
