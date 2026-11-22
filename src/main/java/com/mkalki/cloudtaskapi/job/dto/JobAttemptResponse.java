package com.mkalki.cloudtaskapi.job.dto;

import com.mkalki.cloudtaskapi.job.JobAttemptOutcome;

import java.time.Instant;

public record JobAttemptResponse(
        int attemptNo,
        String workerId,
        Instant startedAt,
        Instant finishedAt,
        JobAttemptOutcome outcome,
        String error,
        Long durationMs
) {
}
