package com.mkalki.cloudtaskapi.job.exception;

/** Thrown when a job payload is larger than the allowed size (413). */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
