package com.mkalki.cloudtaskapi.job.exception;

/** Thrown when a user already has the maximum number of active jobs (429). */
public class ActiveJobLimitExceededException extends RuntimeException {
    public ActiveJobLimitExceededException(String message) {
        super(message);
    }
}
