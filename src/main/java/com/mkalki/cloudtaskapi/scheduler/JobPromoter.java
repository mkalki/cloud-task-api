package com.mkalki.cloudtaskapi.scheduler;

import com.mkalki.cloudtaskapi.job.JobRepository;
import com.mkalki.cloudtaskapi.job.JobStateMachine;
import com.mkalki.cloudtaskapi.job.JobStatus;
import com.mkalki.cloudtaskapi.queue.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One promote run: Redis scheduled -> ready (atomic Lua), then PostgreSQL PENDING -> QUEUED
 * (conditional SQL). Safe to run in several scheduler instances at the same time.
 *
 * If the process dies between the two steps, a job sits in Redis ready while PostgreSQL still
 * says PENDING. Nothing is lost: the reconciler puts it back in scheduled, the next run moves
 * it again (ZADD NX leaves the ready entry alone) and the DB update then succeeds.
 */
@Service
@Profile("scheduler")
public class JobPromoter {

    private static final Logger log = LoggerFactory.getLogger(JobPromoter.class);

    private final QueueService queueService;
    private final JobRepository jobRepository;
    private final JobStateMachine stateMachine;
    private final SchedulerProperties properties;
    private final Clock clock;

    public JobPromoter(
            QueueService queueService,
            JobRepository jobRepository,
            JobStateMachine stateMachine,
            SchedulerProperties properties,
            Clock clock
    ) {
        this.queueService = queueService;
        this.jobRepository = jobRepository;
        this.stateMachine = stateMachine;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return the ids this call moved from PENDING to QUEUED */
    public List<UUID> promoteDue() {
        List<UUID> promoted = queueService.promoteDue(clock.millis(), properties.promoteBatchSize());

        List<UUID> queued = new ArrayList<>();
        for (UUID id : promoted) {
            try {
                if (stateMachine.transitionIf(id, JobStatus.PENDING, JobStatus.QUEUED, clock.instant())) {
                    queued.add(id);
                    log.info("job queued jobId={}", id);
                } else {
                    dropIfNotRunnable(id);
                }
            } catch (RuntimeException e) {
                // The job stays in Redis ready while the row is still PENDING. The reconciler repairs it.
                log.warn("could not mark jobId={} QUEUED, leaving it to the reconciler: {}", id, e.toString());
            }
        }
        return queued;
    }

    /**
     * The conditional update changed no row. If the row is missing or finished (a cancel raced
     * with the promotion), the Redis entry is junk and is removed. If the job is already QUEUED
     * or RUNNING the entry is legitimate and stays.
     */
    private void dropIfNotRunnable(UUID id) {
        Optional<JobStatus> status = jobRepository.findStatusById(id);
        if (status.isEmpty() || status.get().isTerminal()) {
            queueService.remove(id);
            log.info("dropped jobId={} from Redis because its row is {}", id, status.map(Enum::name).orElse("missing"));
        }
    }
}
