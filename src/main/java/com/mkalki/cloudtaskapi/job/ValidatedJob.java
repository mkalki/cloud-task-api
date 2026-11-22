package com.mkalki.cloudtaskapi.job;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** A submit request after validation, with defaults applied and runAt resolved. */
public record ValidatedJob(
        String type,
        JsonNode payload,
        int priority,
        Instant runAt,
        int maxAttempts,
        int timeoutSeconds
) {
}
