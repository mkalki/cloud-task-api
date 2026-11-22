package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cancel, lost leases, late results and claims of rows that are not QUEUED. */
@WorkerTestContext
class WorkerFencingIntegrationTest extends WorkerTestSupport {

    // ---------- cancelled jobs ----------

    @Test
    void aJobCancelledAfterItWasQueuedIsNeverExecuted() throws Exception {
        UUID id = submitJob("demo.work", 5, "{\"counterKey\": \"cancelled-before-claim\"}");
        promoteAll();
        assertThat(statusOf(id)).isEqualTo("QUEUED");
        assertThat(inReady(id)).isTrue();

        cancelJob(token, id.toString()).andExpect(status().isOk());
        // The best-effort Redis removal of the cancel failed (simulated): the id is back in ready.
        queue.enqueueReady(id, 5, System.currentTimeMillis());
        assertThat(inReady(id)).isTrue();

        startWorker(props(1, 30_000));

        await().atMost(PATIENCE).untilAsserted(() -> {
            assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isZero();
            assertThat(redis.opsForZSet().zCard(RedisKeys.PROCESSING)).isZero();
        });
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(outcomesOf(id)).isEmpty();
        assertThat(jdbc.queryForObject("select attempt from jobs where id = ?", Integer.class, id)).isZero();
        assertThat(redis.opsForValue().get(
                RedisKeys.demoCounter(userIdOf(token), "cancelled-before-claim", "runs"))).isNull();
    }

    @Test
    void cancellingARunningJobFencesTheWorkersLateResult() throws Exception {
        UUID id = submitJob(token, "test.controlled", 5, "{}", 300);
        promoteAll();
        startWorker(props(1, 30_000));
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(controlled.hasStarted(id)).isTrue());
        assertThat(statusOf(id)).isEqualTo("RUNNING");

        cancelJob(token, id.toString()).andExpect(status().isOk());
        controlled.open(id);

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(outcomesOf(id)).containsExactly("FENCED"));
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select last_error from jobs where id = ?", String.class, id)).isNull();
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(inProcessing(id)).isFalse());
    }

    // ---------- leases ----------

    @Test
    void aLateResultFromARunThatWasTakenOverIsIgnoredAndRecordedAsFenced() throws Exception {
        UUID id = submitJob(token, "test.controlled", 5, "{}", 300);
        promoteAll();
        startWorker(props(1, 30_000));
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(controlled.hasStarted(id)).isTrue());

        // Another worker took the job over after this one's lease expired: attempt 2, other worker.
        jdbc.update("update jobs set locked_by = 'other-worker', attempt = 2 where id = ?", id);
        controlled.open(id);

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(outcomesOf(id)).containsExactly("FENCED"));
        assertThat(statusOf(id)).as("the other run still owns the job").isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select locked_by from jobs where id = ?", String.class, id))
                .isEqualTo("other-worker");
        assertThat(jdbc.queryForObject("select attempt from jobs where id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select attempt_no from job_attempts where job_id = ?", Integer.class, id))
                .as("the ignored result belongs to the old run").isEqualTo(1);
    }

    @Test
    void whenRedisForgetsTheLeaseTheHandlerIsInterruptedAndNoResultIsReported() throws Exception {
        UUID id = submitJob(token, "test.controlled", 5, "{}", 300);
        promoteAll();
        startWorker(props(1, 600));
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(controlled.hasStarted(id)).isTrue());

        redis.opsForZSet().remove(RedisKeys.PROCESSING, id.toString());

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(controlled.wasInterrupted(id)).isTrue());
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(outcomesOf(id)).containsExactly("FENCED"));
        assertThat(statusOf(id)).as("no result was reported, the reaper of F4 owns the recovery").isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select error from job_attempts where job_id = ?", String.class, id))
                .contains("Lease lost");
    }

    @Test
    void whenTheDatabaseRowIsTakenOverTheHandlerIsInterruptedAndNoResultIsReported() throws Exception {
        UUID id = submitJob(token, "test.controlled", 5, "{}", 300);
        promoteAll();
        startWorker(props(1, 600));
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(controlled.hasStarted(id)).isTrue());

        jdbc.update("update jobs set locked_by = 'other-worker' where id = ?", id);

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(controlled.wasInterrupted(id)).isTrue());
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(outcomesOf(id)).containsExactly("FENCED"));
        assertThat(statusOf(id)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select locked_by from jobs where id = ?", String.class, id))
                .isEqualTo("other-worker");
    }

    @Test
    void aHealthyRunKeepsItsLeaseInBothPlacesWhileItWorks() throws Exception {
        UUID id = submitJob(token, "test.controlled", 5, "{}", 300);
        promoteAll();
        startWorker(props(1, 600));
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(controlled.hasStarted(id)).isTrue());
        Double firstDeadline = redis.opsForZSet().score(RedisKeys.PROCESSING, id.toString());
        java.sql.Timestamp firstLease = jdbc.queryForObject(
                "select lease_expires_at from jobs where id = ?", java.sql.Timestamp.class, id);

        await().atMost(PATIENCE).untilAsserted(() -> {
            Double deadline = redis.opsForZSet().score(RedisKeys.PROCESSING, id.toString());
            assertThat(deadline).isNotNull().isGreaterThan(firstDeadline);
            assertThat(jdbc.queryForObject("select lease_expires_at from jobs where id = ?",
                    java.sql.Timestamp.class, id)).isAfter(firstLease);
        });
        assertThat(controlled.wasInterrupted(id)).isFalse();
        assertThat(statusOf(id)).isEqualTo("RUNNING");
    }

    // ---------- claims that must be dropped ----------

    @Test
    void aClaimOfAJobThatIsStillPendingInTheDatabaseIsDroppedAndTheReconcilerRecoversIt() throws Exception {
        UUID id = submitJob("demo.work", 5, "{\"durationMs\": 10}");
        // The promoter moved the id to ready (step 1) and died before its SQL update (step 2).
        assertThat(queue.promoteDue(System.currentTimeMillis(), 10)).containsExactly(id);
        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(inReady(id)).isTrue();

        startWorker(props(1, 30_000));

        await().atMost(PATIENCE).untilAsserted(() -> {
            assertThat(inReady(id)).isFalse();
            assertThat(inProcessing(id)).isFalse();
        });
        assertThat(statusOf(id)).as("nothing was executed").isEqualTo("PENDING");
        assertThat(outcomesOf(id)).isEmpty();
        assertThat(jdbc.queryForObject("select attempt from jobs where id = ?", Integer.class, id)).isZero();

        // The reconciler puts the PENDING row back in jobs:scheduled; the next promote queues it and the worker runs it.
        reconciler.reconcile();
        promoteAll();

        awaitStatus(id, "SUCCEEDED");
        assertThat(outcomesOf(id)).containsExactly("SUCCEEDED");
    }

    @Test
    void anUnknownJobIdInTheReadyQueueIsDropped() {
        UUID ghost = UUID.randomUUID();
        queue.enqueueReady(ghost, 5, System.currentTimeMillis());

        startWorker(props(1, 30_000));

        await().atMost(PATIENCE).untilAsserted(() -> {
            assertThat(inReady(ghost)).isFalse();
            assertThat(inProcessing(ghost)).isFalse();
        });
    }
}
