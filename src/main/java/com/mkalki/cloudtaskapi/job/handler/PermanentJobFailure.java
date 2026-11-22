package com.mkalki.cloudtaskapi.job.handler;

/**
 * Thrown by a handler when retrying cannot help (for example an HTTP 4xx answer or an invalid
 * payload). From F4 on such a job goes straight to DEAD. Until then every failure goes to DEAD.
 */
public class PermanentJobFailure extends RuntimeException {

    public PermanentJobFailure(String message) {
        super(message);
    }

    public PermanentJobFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
