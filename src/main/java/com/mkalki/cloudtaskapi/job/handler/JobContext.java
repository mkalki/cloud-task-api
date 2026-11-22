package com.mkalki.cloudtaskapi.job.handler;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * What a handler receives when it runs a job.
 *
 * @param ownerId   the user who submitted the job; a handler may only touch this user's data
 * @param cancelled true once the run was abandoned (timeout or lost lease); see {@link #isCancelled()}
 */
public record JobContext(
        UUID jobId,
        Long ownerId,
        String type,
        int attempt,
        JsonNode payload,
        BooleanSupplier cancelled
) {

    /** True when the worker gave up on this run. A handler should stop as soon as it notices. */
    public boolean isCancelled() {
        return cancelled.getAsBoolean();
    }

    /** The payload may contain personal data, so it is left out of the text form. */
    @Override
    public String toString() {
        return "JobContext[jobId=" + jobId + ", type=" + type + ", attempt=" + attempt + "]";
    }
}
