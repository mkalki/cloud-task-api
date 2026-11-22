package com.mkalki.cloudtaskapi.job;

import com.mkalki.cloudtaskapi.job.exception.IllegalJobTransitionException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.mkalki.cloudtaskapi.job.JobStatus.BLOCKED;
import static com.mkalki.cloudtaskapi.job.JobStatus.CANCELLED;
import static com.mkalki.cloudtaskapi.job.JobStatus.DEAD;
import static com.mkalki.cloudtaskapi.job.JobStatus.PENDING;
import static com.mkalki.cloudtaskapi.job.JobStatus.QUEUED;
import static com.mkalki.cloudtaskapi.job.JobStatus.RUNNING;
import static com.mkalki.cloudtaskapi.job.JobStatus.SUCCEEDED;
import static com.mkalki.cloudtaskapi.job.JobStatus.UPSTREAM_FAILED;

/**
 * The only class that changes {@link Job#getStatus()}. Legal moves follow PRD section 8.1.
 *
 * <p>Two ways in: {@link #transition} changes an in-memory entity (saved later with its
 * {@code @Version} check), and {@link #transitionIf} runs one conditional SQL update for
 * changes that can race with other processes (cancel now, workers and scheduler later).
 * Both bump {@code version}, so the two styles fence each other.
 */
@Component
public class JobStateMachine {

    private static final Map<JobStatus, Set<JobStatus>> LEGAL = new EnumMap<>(JobStatus.class);

    static {
        // all parents succeeded -> runnable; parent died -> upstream failed; user cancel
        LEGAL.put(BLOCKED, EnumSet.of(PENDING, UPSTREAM_FAILED, CANCELLED));
        // runAt reached and promoted to the queue; cancel
        LEGAL.put(PENDING, EnumSet.of(QUEUED, CANCELLED));
        // claimed by a worker; cancel
        LEGAL.put(QUEUED, EnumSet.of(RUNNING, CANCELLED));
        // success; retry with backoff (back to PENDING); attempts exhausted or permanent failure; cancel
        LEGAL.put(RUNNING, EnumSet.of(SUCCEEDED, PENDING, DEAD, CANCELLED));
        // admin retry
        LEGAL.put(DEAD, EnumSet.of(PENDING));
        // admin retry of a dead parent re-opens its descendants
        LEGAL.put(UPSTREAM_FAILED, EnumSet.of(BLOCKED));
        LEGAL.put(SUCCEEDED, EnumSet.noneOf(JobStatus.class));
        LEGAL.put(CANCELLED, EnumSet.noneOf(JobStatus.class));
    }

    private final JobRepository jobRepository;

    public JobStateMachine(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public boolean canTransition(JobStatus from, JobStatus to) {
        return LEGAL.get(from).contains(to);
    }

    /**
     * Moves the job to {@code to}, or throws {@link IllegalJobTransitionException}.
     * Sets {@code finishedAt} on entering a terminal status and clears it on leaving one.
     */
    public void transition(Job job, JobStatus to, Instant now) {
        JobStatus from = job.getStatus();
        if (!canTransition(from, to)) {
            throw new IllegalJobTransitionException(from, to);
        }

        job.setStatus(to);
        job.setUpdatedAt(now);

        if (to.isTerminal()) {
            job.setFinishedAt(now);
        } else if (from.isTerminal()) {
            job.setFinishedAt(null);
        }
    }

    /**
     * Moves job {@code id} from {@code from} to {@code to} in the database, but only if it is
     * still in {@code from}. Returns false when another writer changed the status first; the
     * caller must re-read the job and decide again. Throws if the move itself is illegal.
     *
     * <p>{@code finishedAt} is set on entering a terminal status and cleared otherwise.
     * Clearing is safe because a non-terminal job never has a {@code finishedAt}.
     */
    public boolean transitionIf(UUID id, JobStatus from, JobStatus to, Instant now) {
        if (!canTransition(from, to)) {
            throw new IllegalJobTransitionException(from, to);
        }
        Instant finishedAt = to.isTerminal() ? now : null;
        return jobRepository.updateStatusIfCurrent(id, from, to, now, finishedAt) == 1;
    }

    /**
     * A worker takes a job: QUEUED to RUNNING in one conditional update that also adds one to
     * {@code attempt} and records who holds the job and until when. Returns false when the row is
     * not QUEUED (cancelled, or the promoter has not yet finished its own update).
     */
    public boolean claim(UUID id, String workerId, Instant now, Instant leaseExpiresAt) {
        if (!canTransition(QUEUED, RUNNING)) {
            throw new IllegalJobTransitionException(QUEUED, RUNNING);
        }
        return jobRepository.claimIfQueued(id, QUEUED, RUNNING, workerId, now, leaseExpiresAt) == 1;
    }

    /**
     * The fenced result of a run: moves the job out of RUNNING only if it is still RUNNING for this
     * attempt number and this worker. Returns false when the run was fenced off (cancelled, or taken
     * over by another worker after the lease expired); the caller must then ignore its result.
     * Clears the lease and stores {@code lastError} (null on success).
     */
    public boolean transitionIfOwned(
            UUID id, int attempt, String workerId, JobStatus to, String lastError, Instant now) {
        if (!canTransition(RUNNING, to)) {
            throw new IllegalJobTransitionException(RUNNING, to);
        }
        Instant finishedAt = to.isTerminal() ? now : null;
        return jobRepository.updateStatusIfOwned(
                id, RUNNING, to, attempt, workerId, lastError, now, finishedAt) == 1;
    }
}
