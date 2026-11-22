package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One worker, one job at a time: what a normal run, a failure and a timeout leave behind. */
@WorkerTestContext
class WorkerBasicsIntegrationTest extends WorkerTestSupport {

    @Test
    void aJobRunsToSucceededAndLeavesOneSucceededAttemptWithADuration() throws Exception {
        UUID id = submitJob("demo.work", 5, "{\"durationMs\": 30}");
        promoteAll();
        Worker worker = startWorker(props(2, 30_000));

        awaitStatus(id, "SUCCEEDED");

        Map<String, Object> job = jdbc.queryForMap(
                "select attempt, locked_by, lease_expires_at, last_error, started_at, finished_at from jobs where id = ?", id);
        assertThat(job.get("attempt")).isEqualTo(1);
        assertThat(job.get("locked_by")).isEqualTo(worker.workerId());
        assertThat(job.get("lease_expires_at")).as("the lease is cleared when the job ends").isNull();
        assertThat(job.get("last_error")).isNull();
        assertThat(job.get("started_at")).isNotNull();
        assertThat(job.get("finished_at")).isNotNull();

        List<Map<String, Object>> attempts = jdbc.queryForList(
                "select attempt_no, worker_id, outcome, error, duration_ms, started_at, finished_at from job_attempts where job_id = ?", id);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).get("attempt_no")).isEqualTo(1);
        assertThat(attempts.get(0).get("worker_id")).isEqualTo(worker.workerId());
        assertThat(attempts.get(0).get("outcome")).isEqualTo("SUCCEEDED");
        assertThat(attempts.get(0).get("error")).isNull();
        assertThat((Long) attempts.get(0).get("duration_ms")).isGreaterThanOrEqualTo(0);
        assertThat(attempts.get(0).get("finished_at")).isNotNull();

        await().atMost(PATIENCE).untilAsserted(() -> assertThat(inProcessing(id)).isFalse());
        getJob(token, id.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attempts[0].outcome").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attempts[0].workerId").value(worker.workerId()));
    }

    @Test
    void aFailingHandlerMakesTheJobDeadWithTheErrorAndAFailedAttempt() throws Exception {
        UUID id = submitJob("demo.work", 5, "{\"failProbability\": 1}");
        promoteAll();
        startWorker(props(1, 30_000));

        awaitStatus(id, "DEAD");

        assertThat(jdbc.queryForObject("select last_error from jobs where id = ?", String.class, id))
                .contains("failed on purpose");
        assertThat(outcomesOf(id)).containsExactly("FAILED");
        assertThat(jdbc.queryForObject("select error from job_attempts where job_id = ?", String.class, id))
                .contains("failed on purpose");
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(inProcessing(id)).isFalse());
    }

    @Test
    void aHandlerThatRunsPastTheTimeoutIsInterruptedAndRecordedAsTimeout() throws Exception {
        UUID id = submitJob(token, "demo.work", 5, "{\"durationMs\": 20000}", 1);
        promoteAll();
        startWorker(props(1, 30_000));

        awaitStatus(id, "DEAD");

        assertThat(outcomesOf(id)).containsExactly("TIMEOUT");
        assertThat(jdbc.queryForObject("select last_error from jobs where id = ?", String.class, id))
                .contains("timed out after 1 seconds");
        assertThat(jdbc.queryForObject("select duration_ms from job_attempts where job_id = ?", Long.class, id))
                .as("the worker gave up after about the timeout, not after the 20 s of work")
                .isBetween(900L, 10_000L);
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(inProcessing(id)).isFalse());
    }

    @Test
    void aWorkerClaimsOnlyAsManyJobsAsItHasFreeSlots() throws Exception {
        UUID first = submitJob(token, "test.controlled", 5, "{}", 300);
        UUID second = submitJob(token, "test.controlled", 5, "{}", 300);
        UUID third = submitJob(token, "test.controlled", 5, "{}", 300);
        promoteAll();

        Worker worker = startWorker(props(2, 30_000));

        // A started handler means the claim finished and the job was handed over. Counting slots alone
        // could catch a second claim that is still in flight.
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(Stream.of(first, second, third)
                .filter(controlled::hasStarted).count()).isEqualTo(2));
        assertThat(worker.activeJobs()).isEqualTo(2);
        // The third job stays ready while both slots are busy. A claimed-but-waiting job would show up in processing.
        assertThat(redis.opsForZSet().zCard(RedisKeys.PROCESSING)).isEqualTo(2);
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isEqualTo(1);

        controlled.open(first);
        controlled.open(second);
        controlled.open(third);
        awaitStatus(first, "SUCCEEDED");
        awaitStatus(second, "SUCCEEDED");
        awaitStatus(third, "SUCCEEDED");
    }

    @Test
    void anInvalidDemoWorkPayloadIsRejectedAtSubmitWith400() throws Exception {
        submit(token, "{\"type\": \"demo.work\", \"payload\": {\"durationMs\": 70000}}")
                .andExpect(status().isBadRequest());
        submit(token, "{\"type\": \"demo.work\", \"payload\": {\"failProbability\": 2}}")
                .andExpect(status().isBadRequest());
        submit(token, "{\"type\": \"demo.work\", \"payload\": {\"counterKey\": \"no spaces\"}}")
                .andExpect(status().isBadRequest());
        assertThat(jobCountFor(token)).isZero();
    }
}
