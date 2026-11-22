package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Several workers in one JVM against one Postgres and one Redis. */
@WorkerTestContext
class WorkerConcurrencyIntegrationTest extends WorkerTestSupport {

    private static final int JOBS_PER_USER = 50;

    @Test
    void hundredJobsOnThreeWorkersEachGetExactlyOneSucceededAttemptAndNeverRunTwiceAtOnce() throws Exception {
        // Two users, because the rate limiter allows 100 requests per user per minute.
        String other = registerAndLogin("worker-b");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < JOBS_PER_USER; i++) {
            ids.add(submitCounted(token, "a" + i));
            ids.add(submitCounted(other, "b" + i));
        }
        promoteAll();

        List<Worker> workers = List.of(
                startWorker(props(2, 30_000)),
                startWorker(props(2, 30_000)),
                startWorker(props(2, 30_000)));

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "select count(*) from jobs where status = 'SUCCEEDED'", Long.class)).isEqualTo(100L));

        // Exactly one attempt per job, and it succeeded.
        List<Map<String, Object>> perJob = jdbc.queryForList(
                "select job_id, count(*) as attempts, min(outcome) as outcome, max(outcome) as other from job_attempts group by job_id");
        assertThat(perJob).hasSize(100);
        for (Map<String, Object> row : perJob) {
            assertThat(row.get("attempts")).isEqualTo(1L);
            assertThat(row.get("outcome")).isEqualTo("SUCCEEDED");
        }
        assertThat(jdbc.queryForList("select distinct attempt from jobs", Integer.class)).containsExactly(1);

        // The handler counted every start and every overlapping start per job.
        long ownerA = userIdOf(token);
        long ownerB = userIdOf(other);
        for (int i = 0; i < JOBS_PER_USER; i++) {
            assertThat(redis.opsForValue().get(RedisKeys.demoCounter(ownerA, "a" + i, "runs"))).isEqualTo("1");
            assertThat(redis.opsForValue().get(RedisKeys.demoCounter(ownerA, "a" + i, "overlaps"))).isNull();
            assertThat(redis.opsForValue().get(RedisKeys.demoCounter(ownerB, "b" + i, "runs"))).isEqualTo("1");
            assertThat(redis.opsForValue().get(RedisKeys.demoCounter(ownerB, "b" + i, "overlaps"))).isNull();
        }

        // Every attempt was written by one of the three workers.
        Set<String> expectedWorkers = workers.stream().map(Worker::workerId).collect(Collectors.toSet());
        assertThat(jdbc.queryForList("select distinct worker_id from job_attempts", String.class))
                .isNotEmpty()
                .isSubsetOf(expectedWorkers);

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(redis.opsForZSet().zCard(RedisKeys.PROCESSING)).isZero());
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isZero();
    }

    @Test
    void withOneWorkerHigherPriorityJobsFinishBeforeLowerPriorityJobsQueuedEarlier() throws Exception {
        List<UUID> low = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            low.add(submitJob("demo.work", 0, "{\"durationMs\": 20}"));
        }
        List<UUID> high = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            high.add(submitJob("demo.work", 9, "{\"durationMs\": 20}"));
        }
        promoteAll();

        startWorker(props(1, 30_000));

        await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "select count(*) from jobs where status = 'SUCCEEDED'", Long.class)).isEqualTo(5L));

        List<UUID> startOrder = jdbc.queryForList("select id from jobs order by started_at", UUID.class);
        assertThat(startOrder.subList(0, 2)).containsExactlyInAnyOrderElementsOf(high);
        assertThat(startOrder.subList(2, 5)).containsExactlyInAnyOrderElementsOf(low);
    }

    private UUID submitCounted(String userToken, String counterKey) throws Exception {
        return submitJob(userToken, "demo.work", 5,
                "{\"durationMs\": 20, \"counterKey\": \"" + counterKey + "\"}", 300);
    }
}
