package com.mkalki.cloudtaskapi.worker;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * A job this worker moved to RUNNING. {@code attempt} and {@code workerId} are the fence:
 * every later write for this run must match both.
 */
public record ClaimedJob(
        UUID id,
        Long ownerId,
        String type,
        int attempt,
        JsonNode payload,
        int timeoutSeconds,
        String workerId,
        Instant startedAt
) {

    /** The payload may contain personal data, so it is left out of the text form. */
    @Override
    public String toString() {
        return "ClaimedJob[jobId=" + id + ", type=" + type + ", attempt=" + attempt + ", workerId=" + workerId + "]";
    }
}
