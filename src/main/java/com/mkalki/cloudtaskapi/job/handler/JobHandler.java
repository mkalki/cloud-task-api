package com.mkalki.cloudtaskapi.job.handler;

import com.fasterxml.jackson.databind.JsonNode;

/** Runs one kind of job. Handlers must be idempotent because delivery is at-least-once. */
public interface JobHandler {

    /** The job type this handler serves, for example {@code demo.work}. */
    String type();

    /**
     * Checks a payload at submit time. Throw
     * {@link com.mkalki.cloudtaskapi.job.exception.InvalidJobRequestException} (answered with 400)
     * for a payload this handler could never run. The default accepts everything.
     */
    default void validatePayload(JsonNode payload) {
    }

    /**
     * Does the work. Throwing any exception fails the attempt. Long-running code should call
     * {@link JobContext#isCancelled()} now and then and must not swallow {@link InterruptedException}:
     * a timeout or a lost lease interrupts the thread, and code that ignores the interrupt cannot be stopped.
     */
    void handle(JobContext ctx);
}
