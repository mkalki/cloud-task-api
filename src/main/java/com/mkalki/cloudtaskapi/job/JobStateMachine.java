package com.mkalki.cloudtaskapi.job;

import com.mkalki.cloudtaskapi.job.exception.IllegalJobTransitionException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
 * It changes the in-memory entity. Later features that need atomic cross-process
 * updates guard them with conditional SQL and still check {@link #canTransition} first.
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
}
