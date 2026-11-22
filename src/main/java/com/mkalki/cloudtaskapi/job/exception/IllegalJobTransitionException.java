package com.mkalki.cloudtaskapi.job.exception;

import com.mkalki.cloudtaskapi.job.JobStatus;

/** Thrown by the state machine when a status change is not in the transition table. */
public class IllegalJobTransitionException extends IllegalStateException {

    private final JobStatus from;
    private final JobStatus to;

    public IllegalJobTransitionException(JobStatus from, JobStatus to) {
        super("Illegal job transition: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public JobStatus getFrom() {
        return from;
    }

    public JobStatus getTo() {
        return to;
    }
}
