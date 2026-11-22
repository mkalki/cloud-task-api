package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.job.JobRepository;
import com.mkalki.cloudtaskapi.job.handler.JobHandlerRegistry;
import com.mkalki.cloudtaskapi.queue.QueueService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Builds {@link Worker}s with their dependencies. The worker role config uses it for the one
 * worker of a process; tests use it to run several workers in one JVM.
 */
@Component
@Profile("worker")
public class WorkerFactory {

    private final WorkerProperties properties;
    private final QueueService queueService;
    private final JobRunService runService;
    private final JobRepository jobRepository;
    private final JobHandlerRegistry registry;
    private final Clock clock;

    public WorkerFactory(
            WorkerProperties properties,
            QueueService queueService,
            JobRunService runService,
            JobRepository jobRepository,
            JobHandlerRegistry registry,
            Clock clock
    ) {
        this.properties = properties;
        this.queueService = queueService;
        this.runService = runService;
        this.jobRepository = jobRepository;
        this.registry = registry;
        this.clock = clock;
    }

    public Worker create() {
        return create(properties);
    }

    /** A new worker with its own id and the given settings. It is not started. */
    public Worker create(WorkerProperties custom) {
        return new Worker(WorkerIds.newId(), custom, queueService, runService, jobRepository, registry, clock);
    }
}
