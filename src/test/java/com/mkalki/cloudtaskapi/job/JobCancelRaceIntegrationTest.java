package com.mkalki.cloudtaskapi.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Cancel against a concurrent "worker completes the job" update. Both go through
 * {@link JobStateMachine#transitionIf}, so exactly one of them wins and the job never
 * moves from SUCCEEDED to CANCELLED (or back).
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobCancelRaceIntegrationTest extends JobApiTestSupport {

    @Autowired
    private JobStateMachine stateMachine;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    /**
     * Deterministic interleaving: the "worker" updates the row to SUCCEEDED but does not
     * commit, so it holds the row lock. Cancel then reads RUNNING (the last committed value)
     * and its conditional update blocks on that lock. When the worker commits, PostgreSQL
     * re-checks {@code status = 'RUNNING'} on the new row, matches nothing, and cancel
     * re-reads, sees SUCCEEDED and answers 409.
     */
    @Test
    void workerCompletionThatCommitsWhileCancelIsWaitingWins() throws Exception {
        String token = registerAndLogin("job-race-complete");
        UUID id = UUID.fromString(submitOk(token, DEMO_JOB));
        jdbc.update("update jobs set status = 'RUNNING', version = version + 1 where id = ?", id);

        CountDownLatch workerUpdated = new CountDownLatch(1);
        CountDownLatch commitWorker = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> worker = pool.submit(() -> transactionTemplate.execute(status -> {
                boolean won = stateMachine.transitionIf(id, JobStatus.RUNNING, JobStatus.SUCCEEDED, clock.instant());
                workerUpdated.countDown();
                awaitQuietly(commitWorker);
                return won;
            }));
            assertThat(workerUpdated.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> cancel = pool.submit(() ->
                    cancelJob(token, id.toString()).andReturn().getResponse().getStatus());

            await().atMost(Duration.ofSeconds(10)).until(this::someSessionIsWaitingOnALock);
            commitWorker.countDown();

            assertThat(worker.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cancel.get(10, TimeUnit.SECONDS)).isEqualTo(409);
        } finally {
            commitWorker.countDown();
            pool.shutdownNow();
        }

        assertThat(statusOf(id)).isEqualTo("SUCCEEDED");
        assertThat(cancelAuditRows(token)).isZero();
    }

    @Test
    void cancelThatCommitsFirstMakesTheWorkerCompletionFail() throws Exception {
        String token = registerAndLogin("job-race-cancel");
        UUID id = UUID.fromString(submitOk(token, DEMO_JOB));
        jdbc.update("update jobs set status = 'RUNNING', version = version + 1 where id = ?", id);

        assertThat(cancelJob(token, id.toString()).andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(stateMachine.transitionIf(id, JobStatus.RUNNING, JobStatus.SUCCEEDED, clock.instant()))
                .isFalse();
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
    }

    @Test
    void conditionalCancelBumpsVersionSoAStaleEntityCannotOverwriteIt() throws Exception {
        String token = registerAndLogin("job-race-stale");
        UUID id = UUID.fromString(submitOk(token, DEMO_JOB));
        Job staleCopy = jobRepository.findById(id).orElseThrow();
        long versionBefore = staleCopy.getVersion();

        assertThat(cancelJob(token, id.toString()).andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbc.queryForObject("select version from jobs where id = ?", Long.class, id))
                .isEqualTo(versionBefore + 1);
        stateMachine.transition(staleCopy, JobStatus.QUEUED, clock.instant());
        assertThatThrownBy(() -> jobRepository.save(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
    }

    @Test
    void cancelOfAJobCompletedAfterItWasQueuedReturns409() throws Exception {
        String token = registerAndLogin("job-race-done");
        UUID id = UUID.fromString(submitOk(token, DEMO_JOB));
        stateMachine.transitionIf(id, JobStatus.PENDING, JobStatus.QUEUED, clock.instant());
        stateMachine.transitionIf(id, JobStatus.QUEUED, JobStatus.RUNNING, clock.instant());
        stateMachine.transitionIf(id, JobStatus.RUNNING, JobStatus.SUCCEEDED, clock.instant());

        assertThat(cancelJob(token, id.toString()).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(statusOf(id)).isEqualTo("SUCCEEDED");
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("select status from jobs where id = ?", String.class, id);
    }

    private long cancelAuditRows(String token) {
        return jdbc.queryForObject(
                "select count(*) from audit_logs where user_id = ? and action = 'JOB_CANCELLED'",
                Long.class, userIdOf(token));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
