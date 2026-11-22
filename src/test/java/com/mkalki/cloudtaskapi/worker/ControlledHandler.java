package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.job.handler.JobContext;
import com.mkalki.cloudtaskapi.job.handler.JobHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * A handler the test controls: it starts, then waits until the test opens its gate.
 * It records when it started and whether it was interrupted, so tests can wait for both.
 */
class ControlledHandler implements JobHandler {

    static final String TYPE = "test.controlled";

    private final Set<UUID> started = ConcurrentHashMap.newKeySet();
    private final Set<UUID> interrupted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CountDownLatch> gates = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobContext ctx) {
        started.add(ctx.jobId());
        try {
            gate(ctx.jobId()).await();
        } catch (InterruptedException e) {
            interrupted.add(ctx.jobId());
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
    }

    boolean hasStarted(UUID jobId) {
        return started.contains(jobId);
    }

    boolean wasInterrupted(UUID jobId) {
        return interrupted.contains(jobId);
    }

    /** Lets the handler of this job return normally. */
    void open(UUID jobId) {
        gate(jobId).countDown();
    }

    void openAll() {
        gates.values().forEach(CountDownLatch::countDown);
    }

    private CountDownLatch gate(UUID jobId) {
        return gates.computeIfAbsent(jobId, id -> new CountDownLatch(1));
    }
}
