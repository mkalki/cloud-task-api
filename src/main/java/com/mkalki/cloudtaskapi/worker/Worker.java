package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.job.JobRepository;
import com.mkalki.cloudtaskapi.job.handler.JobHandlerRegistry;
import com.mkalki.cloudtaskapi.queue.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One worker: a poll loop that claims jobs while it has a free slot, and a bounded thread pool that
 * runs them. A SmartLifecycle bean, so starting it never blocks application startup.
 *
 * <p>A slot is taken before a job is claimed, so a job is never claimed that this worker cannot start.
 * With no job ready the loop waits {@code poll-min-ms}, doubling up to {@code poll-max-ms}.
 *
 * <p>Stopping: no new claims, then up to {@value #STOP_WAIT_SECONDS} seconds for running jobs to
 * finish. What is still running is interrupted and reports nothing; its lease expires and the
 * reaper of F4 recovers the job. F3c makes the wait configurable (cloudtask.worker.shutdown-grace).
 */
public class Worker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private static final long SLOT_WAIT_MS = 100;
    static final long STOP_WAIT_SECONDS = 5;

    private final String workerId;
    private final WorkerProperties properties;
    private final QueueService queueService;
    private final JobRunService runService;
    private final JobRepository jobRepository;
    private final JobHandlerRegistry registry;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean();
    private final Semaphore slots;

    private ThreadPoolExecutor jobPool;
    private ExecutorService handlerPool;
    private ScheduledThreadPoolExecutor leaseTimer;
    private JobAttemptRunner runner;
    private Thread pollThread;

    Worker(
            String workerId,
            WorkerProperties properties,
            QueueService queueService,
            JobRunService runService,
            JobRepository jobRepository,
            JobHandlerRegistry registry,
            Clock clock
    ) {
        this.workerId = workerId;
        this.properties = properties;
        this.queueService = queueService;
        this.runService = runService;
        this.jobRepository = jobRepository;
        this.registry = registry;
        this.clock = clock;
        this.slots = new Semaphore(properties.concurrency());
    }

    public String workerId() {
        return workerId;
    }

    /** Jobs this worker is running right now. */
    public int activeJobs() {
        return properties.concurrency() - slots.availablePermits();
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        int concurrency = properties.concurrency();
        jobPool = new ThreadPoolExecutor(
                concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(concurrency), threads("job"));
        // One handler thread per running job. A handler that ignores an interrupt keeps its thread,
        // so this pool is not capped: capping it would make the next job wait behind a stuck one.
        handlerPool = Executors.newCachedThreadPool(threads("handler"));
        leaseTimer = new ScheduledThreadPoolExecutor(2, threads("lease"));
        leaseTimer.setRemoveOnCancelPolicy(true);
        runner = new JobAttemptRunner(
                workerId, properties, queueService, runService, jobRepository, registry, clock,
                handlerPool, leaseTimer);

        running.set(true);
        pollThread = threads("poll").newThread(this::pollLoop);
        pollThread.start();
        log.info("worker started workerId={} concurrency={} leaseMs={}",
                workerId, properties.concurrency(), properties.leaseMs());
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            // The loop wakes at least every poll-max-ms, sees running == false and leaves.
            pollThread.join(properties.pollMaxMs() + 2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (pollThread.isAlive()) {
            pollThread.interrupt();
        }

        jobPool.shutdown();
        try {
            if (!jobPool.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("worker stopping with jobs still running, their leases will expire workerId={}", workerId);
                jobPool.shutdownNow();
                jobPool.awaitTermination(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            jobPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        handlerPool.shutdownNow();
        leaseTimer.shutdownNow();
        log.info("worker stopped workerId={}", workerId);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void pollLoop() {
        long pauseMs = properties.pollMinMs();
        while (running.get()) {
            try {
                if (!slots.tryAcquire(SLOT_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    continue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            boolean idle = false;
            boolean handedOver = false;
            try {
                Optional<UUID> id = queueService.claim(clock.millis() + properties.leaseMs());
                if (id.isEmpty()) {
                    idle = true;
                } else {
                    handedOver = runClaimed(id.get());
                }
            } catch (RuntimeException e) {
                log.warn("poll failed, backing off workerId={}: {}", workerId, e.toString());
                idle = true;
            } finally {
                if (!handedOver) {
                    slots.release();
                }
            }

            if (idle) {
                if (!pause(pauseMs)) {
                    return;
                }
                pauseMs = Math.min(pauseMs * 2, properties.pollMaxMs());
            } else {
                pauseMs = properties.pollMinMs();
            }
        }
    }

    /**
     * The job is in jobs:processing. Now the database decides: one conditional update QUEUED to RUNNING.
     *
     * @return true if the job was handed to the pool (the slot is then released when it ends)
     */
    private boolean runClaimed(UUID id) {
        Optional<ClaimedJob> claimed;
        try {
            claimed = runService.claim(id, workerId, properties.leaseMs());
        } catch (RuntimeException e) {
            // Database trouble after the Redis claim. Give the job back; the reconciler puts a QUEUED row into ready again.
            releaseQuietly(id);
            throw e;
        }

        if (claimed.isEmpty()) {
            // Cancelled or finished, or still PENDING because the promoter has not finished its SQL step.
            // The reconciler restores a PENDING job within its interval.
            queueService.release(id);
            log.info("claim dropped, the row is not QUEUED jobId={} workerId={}", id, workerId);
            return false;
        }

        ClaimedJob job = claimed.get();
        log.info("job claimed jobId={} type={} attempt={} workerId={}",
                job.id(), job.type(), job.attempt(), workerId);
        try {
            jobPool.execute(() -> runJob(job));
        } catch (RejectedExecutionException e) {
            // The worker is stopping. The row stays RUNNING and its lease expires.
            log.warn("worker is stopping, job not started jobId={} workerId={}", job.id(), workerId);
            return false;
        }
        return true;
    }

    private void runJob(ClaimedJob job) {
        try {
            runner.run(job);
        } catch (RuntimeException e) {
            log.error("job run failed unexpectedly jobId={} type={} attempt={} workerId={}: {}",
                    job.id(), job.type(), job.attempt(), workerId, e.toString());
        } finally {
            slots.release();
        }
    }

    private void releaseQuietly(UUID id) {
        try {
            queueService.release(id);
        } catch (RuntimeException e) {
            log.warn("could not remove jobId={} from jobs:processing: {}", id, e.toString());
        }
    }

    /** @return false if the thread was interrupted and should leave the loop */
    private boolean pause(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ThreadFactory threads(String role) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "worker-" + workerId + "-" + role + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
