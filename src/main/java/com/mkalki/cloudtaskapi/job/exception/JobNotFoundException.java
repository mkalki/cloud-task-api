package com.mkalki.cloudtaskapi.job.exception;

/** Thrown when a job does not exist or belongs to another user (both return 404). */
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String message) {
        super(message);
    }
}
