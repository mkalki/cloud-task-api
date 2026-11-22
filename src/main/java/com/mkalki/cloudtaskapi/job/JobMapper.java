package com.mkalki.cloudtaskapi.job;

import com.mkalki.cloudtaskapi.job.dto.JobAttemptResponse;
import com.mkalki.cloudtaskapi.job.dto.JobResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobMapper {

    public JobResponse toResponse(Job job) {
        return toResponse(job, null);
    }

    public JobResponse toResponse(Job job, List<JobAttempt> attempts) {
        return new JobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getPriority(),
                job.getAttempt(),
                job.getMaxAttempts(),
                job.getTimeoutSeconds(),
                job.getRunAt(),
                job.getPayload(),
                job.getWorkflowId(),
                job.getIdempotencyKey(),
                job.getLastError(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                attempts == null ? null : attempts.stream().map(this::toAttemptResponse).toList()
        );
    }

    private JobAttemptResponse toAttemptResponse(JobAttempt attempt) {
        return new JobAttemptResponse(
                attempt.getAttemptNo(),
                attempt.getWorkerId(),
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                attempt.getOutcome(),
                attempt.getError(),
                attempt.getDurationMs()
        );
    }
}
