package com.mkalki.cloudtaskapi.job;

public enum JobAttemptOutcome {
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    WORKER_LOST,
    FENCED
}
