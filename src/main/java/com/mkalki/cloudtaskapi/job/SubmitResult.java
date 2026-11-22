package com.mkalki.cloudtaskapi.job;

import com.mkalki.cloudtaskapi.job.dto.JobResponse;

/** {@code created} is false when an Idempotency-Key matched an existing job. */
public record SubmitResult(JobResponse job, boolean created) {
}
