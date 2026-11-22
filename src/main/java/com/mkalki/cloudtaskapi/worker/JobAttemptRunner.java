package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.job.JobAttemptOutcome;
import com.mkalki.cloudtaskapi.job.JobRepository;
import com.mkalki.cloudtaskapi.job.JobStatus;
import com.mkalki.cloudtaskapi.job.handler.JobContext;
import com.mkalki.cloudtaskapi.job.handler.JobHandler;
import com.mkalki.cloudtaskapi.job.handler.JobHandlerRegistry;
import com.mkalki.cloudtaskapi.queue.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs one claimed job: the handler in a Future, the lease renewal on a timer, then the fenced result.
 *
 * <p>Timeouts and lost leases use {@code Future.cancel(true)}, which only interrupts. A handler that
 * ignores interrupts keeps running and cannot be killed from Java. The lease then expires (the
 * database row stops being renewed) and the reaper of F4 takes the job over; the late result of the
 * old run is rejected by the fence.
 */
class JobAttemptRunner {

    private static final Logger log = LoggerFactory.getLogger(JobAttemptRunner.class);

    private final String workerId;
    private final WorkerProperties properties;
    private final QueueService queueService;
    private final JobRunService runService;
    private final JobRepository jobRepository;
    private final JobHandlerRegistry registry;
    private final Clock clock;
    private final ExecutorService handlerPool;
    private final ScheduledExecutorService leaseTimer;

    JobAttemptRunner(
            String workerId,
            WorkerProperties properties,
            QueueService queueService,
            JobRunService runService,
            JobRepository jobRepository,
            JobHandlerRegistry registry,
            Clock clock,
            ExecutorService handlerPool,
            ScheduledExecutorService leaseTimer
    ) {
        this.workerId = workerId;
        this.properties = properties;
        this.queueService = queueService;
        this.runService = runService;
        this.jobRepository = jobRepository;
        this.registry = registry;
        this.clock = clock;
        this.handlerPool = handlerPool;
        this.leaseTimer = leaseTimer;
    }

    void run(ClaimedJob job) {
        log.info("job running jobId={} type={} attempt={} workerId={}",
                job.id(), job.type(), job.attempt(), workerId);

        JobHandler handler = registry.find(job.type()).orElse(null);
        if (handler == null) {
            report(job, new Result(JobStatus.DEAD, JobAttemptOutcome.FAILED,
                    "No handler is registered for job type " + job.type()));
            return;
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean leaseLost = new AtomicBoolean();
        JobContext context = new JobContext(
                job.id(), job.ownerId(), job.type(), job.attempt(), job.payload(), cancelled::get);

        Future<?> future = handlerPool.submit(() -> {
            handler.handle(context);
            return null;
        });

        long interval = properties.renewIntervalMs();
        ScheduledFuture<?> renewal = leaseTimer.scheduleWithFixedDelay(
                () -> renewLease(job, future, leaseLost, cancelled), interval, interval, TimeUnit.MILLISECONDS);

        Result result;
        try {
            future.get(job.timeoutSeconds(), TimeUnit.SECONDS);
            result = new Result(JobStatus.SUCCEEDED, JobAttemptOutcome.SUCCEEDED, null);
        } catch (TimeoutException e) {
            cancelled.set(true);
            future.cancel(true);
            result = new Result(JobStatus.DEAD, JobAttemptOutcome.TIMEOUT,
                    "Handler timed out after " + job.timeoutSeconds() + " seconds");
        } catch (ExecutionException e) {
            result = new Result(JobStatus.DEAD, JobAttemptOutcome.FAILED, describe(e.getCause()));
        } catch (CancellationException e) {
            // The lease renewal cancelled the handler because the lease is lost.
            stopped(job, leaseLost.get());
            return;
        } catch (InterruptedException e) {
            // This thread was interrupted: the worker is shutting down. No result is reported.
            cancelled.set(true);
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("job interrupted by worker shutdown, its lease will expire jobId={} type={} attempt={} workerId={}",
                    job.id(), job.type(), job.attempt(), workerId);
            return;
        } finally {
            renewal.cancel(false);
        }

        report(job, result);
    }

    /**
     * Extends the lease in Redis and in PostgreSQL. If either says the job is no longer ours,
     * the handler is interrupted and no result will be reported.
     */
    private void renewLease(ClaimedJob job, Future<?> future, AtomicBoolean leaseLost, AtomicBoolean cancelled) {
        if (future.isDone()) {
            return;
        }
        try {
            boolean inRedis = queueService.renewLease(job.id(), clock.millis() + properties.leaseMs());
            boolean inDatabase = runService.renewLease(job, properties.leaseMs());
            if (inRedis && inDatabase) {
                return;
            }
            log.warn("lease lost jobId={} type={} attempt={} workerId={} inRedis={} inDatabase={}",
                    job.id(), job.type(), job.attempt(), workerId, inRedis, inDatabase);
            leaseLost.set(true);
            cancelled.set(true);
            future.cancel(true);
        } catch (RuntimeException e) {
            // A Redis or database blip. The next tick tries again; if the lease really expires, the fence protects us.
            log.warn("lease renewal failed, will retry jobId={} workerId={}: {}", job.id(), workerId, e.toString());
        }
    }

    private void stopped(ClaimedJob job, boolean leaseLost) {
        if (!leaseLost) {
            log.info("job stopped without a result jobId={} type={} attempt={} workerId={}",
                    job.id(), job.type(), job.attempt(), workerId);
            return;
        }
        try {
            runService.recordLostLease(job);
            log.warn("job lost its lease, no result reported jobId={} type={} attempt={} workerId={}",
                    job.id(), job.type(), job.attempt(), workerId);
            releaseIfFinished(job.id());
        } catch (RuntimeException e) {
            log.error("could not record lost lease jobId={} workerId={}: {}", job.id(), workerId, e.toString());
        }
    }

    private void report(ClaimedJob job, Result result) {
        try {
            boolean accepted = runService.finish(job, result.status(), result.outcome(), result.error());
            if (accepted) {
                log.info("job finished jobId={} type={} attempt={} workerId={} status={} outcome={}",
                        job.id(), job.type(), job.attempt(), workerId, result.status(), result.outcome());
                release(job.id());
            } else {
                log.warn("fenced result ignored jobId={} type={} attempt={} workerId={} outcome={}",
                        job.id(), job.type(), job.attempt(), workerId, result.outcome());
                releaseIfFinished(job.id());
            }
        } catch (RuntimeException e) {
            // The job stays RUNNING; its lease expires and the reaper (F4) recovers it.
            log.error("could not record result jobId={} type={} attempt={} workerId={}: {}",
                    job.id(), job.type(), job.attempt(), workerId, e.toString());
        }
    }

    /**
     * After a fenced result another run may legitimately hold this job in jobs:processing, so the
     * entry is only removed when the row is finished (for example cancelled) or gone.
     */
    private void releaseIfFinished(UUID id) {
        Optional<JobStatus> status = jobRepository.findStatusById(id);
        if (status.isEmpty() || status.get().isTerminal()) {
            release(id);
        }
    }

    private void release(UUID id) {
        try {
            queueService.release(id);
        } catch (RuntimeException e) {
            log.warn("could not remove jobId={} from jobs:processing, the reaper will: {}", id, e.toString());
        }
    }

    private static String describe(Throwable cause) {
        if (cause == null) {
            return "Unknown error";
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private record Result(JobStatus status, JobAttemptOutcome outcome, String error) {
    }
}
