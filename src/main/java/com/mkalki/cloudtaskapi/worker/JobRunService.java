package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.job.Job;
import com.mkalki.cloudtaskapi.job.JobAttempt;
import com.mkalki.cloudtaskapi.job.JobAttemptOutcome;
import com.mkalki.cloudtaskapi.job.JobAttemptRepository;
import com.mkalki.cloudtaskapi.job.JobRepository;
import com.mkalki.cloudtaskapi.job.JobStateMachine;
import com.mkalki.cloudtaskapi.job.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The database side of running a job: claim, lease renewal and the fenced result.
 * Every status change goes through {@link JobStateMachine}; this class only decides what to write.
 */
@Service
@Profile("worker")
public class JobRunService {

    private static final Logger log = LoggerFactory.getLogger(JobRunService.class);

    /** last_error and the error of an attempt are cut to this many characters. */
    static final int MAX_ERROR_CHARS = 4096;

    private final JobRepository jobRepository;
    private final JobAttemptRepository attemptRepository;
    private final JobStateMachine stateMachine;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public JobRunService(
            JobRepository jobRepository,
            JobAttemptRepository attemptRepository,
            JobStateMachine stateMachine,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.stateMachine = stateMachine;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * One conditional update QUEUED to RUNNING. Empty means the row is not QUEUED: it was cancelled,
     * it is already finished, or it is still PENDING because the promoter has not run its SQL step yet.
     * The caller then drops the Redis claim.
     *
     * @param leaseMs how long the new lease lasts
     */
    public Optional<ClaimedJob> claim(UUID id, String workerId, long leaseMs) {
        Instant now = clock.instant();
        if (!stateMachine.claim(id, workerId, now, now.plusMillis(leaseMs))) {
            return Optional.empty();
        }

        // The row is RUNNING and held by this worker, so nobody else changes attempt or payload now
        // (a cancel changes the status only).
        Optional<Job> row = jobRepository.findById(id);
        if (row.isEmpty() || !workerId.equals(row.get().getLockedBy())) {
            log.warn("claimed job could not be read back jobId={} workerId={}", id, workerId);
            return Optional.empty();
        }
        Job job = row.get();
        return Optional.of(new ClaimedJob(
                job.getId(),
                job.getOwnerId(),
                job.getType(),
                job.getAttempt(),
                job.getPayload(),
                job.getTimeoutSeconds(),
                workerId,
                job.getStartedAt()));
    }

    /** @return false when the row is no longer RUNNING for this attempt and worker, so the lease is lost */
    public boolean renewLease(ClaimedJob job, long leaseMs) {
        Instant expires = clock.instant().plusMillis(leaseMs);
        return jobRepository.extendLeaseIfOwned(
                job.id(), JobStatus.RUNNING, job.attempt(), job.workerId(), expires) == 1;
    }

    /**
     * Records the result of one attempt. The job update is fenced (id, RUNNING, attempt, worker).
     * If it changes no row the result is ignored and the attempt is stored as FENCED instead.
     * The job update and the attempt row commit together.
     *
     * @param to      the status to move to (SUCCEEDED or DEAD)
     * @param outcome what to store on the attempt when the result is accepted
     * @return true if the result was accepted
     */
    public boolean finish(ClaimedJob job, JobStatus to, JobAttemptOutcome outcome, String error) {
        Instant finishedAt = clock.instant();
        String storedError = truncate(error);

        Boolean accepted = transactionTemplate.execute(status -> {
            boolean ok = stateMachine.transitionIfOwned(
                    job.id(), job.attempt(), job.workerId(), to, storedError, finishedAt);
            if (ok) {
                attemptRepository.save(attemptRow(job, finishedAt, outcome, storedError));
            } else {
                String ignored = "Result ignored (" + outcome + ")" + (storedError == null ? "" : ": " + storedError);
                attemptRepository.save(attemptRow(job, finishedAt, JobAttemptOutcome.FENCED, truncate(ignored)));
            }
            return ok;
        });
        return Boolean.TRUE.equals(accepted);
    }

    /** The lease was lost while the handler ran. The job row is not touched; only the attempt is recorded. */
    public void recordLostLease(ClaimedJob job) {
        attemptRepository.save(attemptRow(job, clock.instant(), JobAttemptOutcome.FENCED,
                "Lease lost: the handler was interrupted and no result was reported"));
    }

    static String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_CHARS) {
            return text;
        }
        return text.substring(0, MAX_ERROR_CHARS);
    }

    private JobAttempt attemptRow(ClaimedJob job, Instant finishedAt, JobAttemptOutcome outcome, String error) {
        long durationMs = Math.max(0, Duration.between(job.startedAt(), finishedAt).toMillis());
        return new JobAttempt(
                job.id(), job.attempt(), job.workerId(), job.startedAt(), finishedAt, outcome, error, durationMs);
    }
}
