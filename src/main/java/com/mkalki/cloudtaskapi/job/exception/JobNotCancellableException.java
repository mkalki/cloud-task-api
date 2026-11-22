package com.mkalki.cloudtaskapi.job.exception;

/** Thrown when a job is already in a finished state and cannot be cancelled (409). */
public class JobNotCancellableException extends RuntimeException {
    public JobNotCancellableException(String message) {
        super(message);
    }
}
