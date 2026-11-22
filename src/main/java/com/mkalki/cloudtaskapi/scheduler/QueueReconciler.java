package com.mkalki.cloudtaskapi.scheduler;

import com.mkalki.cloudtaskapi.job.JobRepository;
import com.mkalki.cloudtaskapi.job.JobStatus;
import com.mkalki.cloudtaskapi.job.QueueEntry;
import com.mkalki.cloudtaskapi.queue.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Rebuilds the Redis queues from PostgreSQL. Every step is idempotent (ZADD writes the same
 * score, enqueue_ready.lua adds only when absent), so any number of instances may run it at
 * once and an interrupted run can simply be repeated.
 *
 * - PENDING rows are put in jobs:scheduled.
 * - QUEUED rows that are in neither jobs:ready nor jobs:processing are put in jobs:ready.
 *
 * Rows are read in pages ordered by id. The cursor is the last id the database returned and is
 * handed back to the database unchanged, so Java never has to compare UUIDs (Java and
 * PostgreSQL order them differently).
 */
@Service
@Profile("scheduler")
public class QueueReconciler {

    private static final Logger log = LoggerFactory.getLogger(QueueReconciler.class);
    private static final UUID LOWEST_UUID = new UUID(0L, 0L);

    private final JobRepository jobRepository;
    private final QueueService queueService;
    private final SchedulerProperties properties;

    public QueueReconciler(JobRepository jobRepository, QueueService queueService, SchedulerProperties properties) {
        this.jobRepository = jobRepository;
        this.queueService = queueService;
        this.properties = properties;
    }

    public ReconcileResult reconcile() {
        int pending = 0;
        int restored = 0;

        UUID cursor = LOWEST_UUID;
        List<QueueEntry> page;
        do {
            page = nextPage(JobStatus.PENDING, cursor);
            for (QueueEntry entry : page) {
                queueService.enqueueScheduled(entry.getId(), entry.getPriority(), entry.getRunAt().toEpochMilli());
                pending++;
            }
            cursor = page.isEmpty() ? cursor : page.get(page.size() - 1).getId();
        } while (page.size() == properties.reconcileBatchSize());

        cursor = LOWEST_UUID;
        do {
            page = nextPage(JobStatus.QUEUED, cursor);
            for (QueueEntry entry : page) {
                if (queueService.enqueueReady(entry.getId(), entry.getPriority(), entry.getRunAt().toEpochMilli())) {
                    restored++;
                }
            }
            cursor = page.isEmpty() ? cursor : page.get(page.size() - 1).getId();
        } while (page.size() == properties.reconcileBatchSize());

        if (restored > 0) {
            log.info("reconciled queues: pendingEnsured={} queuedRestored={}", pending, restored);
        } else {
            log.debug("reconciled queues: pendingEnsured={} queuedRestored=0", pending);
        }
        return new ReconcileResult(pending, restored);
    }

    private List<QueueEntry> nextPage(JobStatus status, UUID cursor) {
        return jobRepository.findByStatusAndIdGreaterThanOrderByIdAsc(
                status, cursor, Limit.of(properties.reconcileBatchSize()));
    }

    /**
     * @param pendingEnsured PENDING rows written to jobs:scheduled (a no-op for those already there)
     * @param queuedRestored QUEUED rows that were in neither ready nor processing and were added to ready
     */
    public record ReconcileResult(int pendingEnsured, int queuedRestored) {
    }
}
