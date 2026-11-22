package com.mkalki.cloudtaskapi.job.handler;

/** Runs one kind of job. Handlers must be idempotent because delivery is at-least-once. */
public interface JobHandler {

    /** The job type this handler serves, for example {@code demo.work}. */
    String type();

    void handle(JobContext ctx);
}
