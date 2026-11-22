package com.mkalki.cloudtaskapi.job.exception;

/** Thrown when a job request breaks a rule that Bean Validation cannot express (400). */
public class InvalidJobRequestException extends RuntimeException {
    public InvalidJobRequestException(String message) {
        super(message);
    }
}
