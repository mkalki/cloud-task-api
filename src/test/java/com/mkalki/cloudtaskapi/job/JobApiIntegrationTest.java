package com.mkalki.cloudtaskapi.job;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobApiIntegrationTest extends JobApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobAttemptRepository attemptRepository;

    @Autowired
    private JobStateMachine stateMachine;

    @Autowired
    private Clock clock;

    // ---------- submit ----------

    @Test
    void submitCreatesPendingJobWithDefaults() throws Exception {
        String token = registerAndLogin("job-create");

        String response = submit(token, """
                {"type": "demo.work", "payload": {"durationMs": 250}}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/jobs/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("demo.work"))
                .andExpect(jsonPath("$.priority").value(5))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.timeoutSeconds").value(300))
                .andExpect(jsonPath("$.attempt").value(0))
                .andExpect(jsonPath("$.payload.durationMs").value(250))
                .andExpect(jsonPath("$.finishedAt").doesNotExist())
                .andExpect(jsonPath("$.attempts").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(response, "$.id");
        assertThat(jdbc.queryForObject("select status from jobs where id = ?::uuid", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void submitHonoursPriorityDelayAndLimits() throws Exception {
        String token = registerAndLogin("job-options");

        String response = submit(token, """
                {"type": "demo.work", "priority": 9, "delaySeconds": 3600,
                 "maxAttempts": 7, "timeoutSeconds": 120}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value(9))
                .andExpect(jsonPath("$.maxAttempts").value(7))
                .andExpect(jsonPath("$.timeoutSeconds").value(120))
                .andReturn().getResponse().getContentAsString();

        Instant runAt = Instant.parse(JsonPath.read(response, "$.runAt"));
        assertThat(runAt).isAfter(Instant.now().plusSeconds(3500));
        assertThat(runAt).isBefore(Instant.now().plusSeconds(3700));
    }

    @Test
    void unknownTypeReturns400() throws Exception {
        String token = registerAndLogin("job-unknown-type");

        submit(token, """
                {"type": "does.not.exist"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("Unknown job type: does.not.exist"));
    }

    @Test
    void missingTypeReturns400() throws Exception {
        String token = registerAndLogin("job-no-type");

        submit(token, "{\"payload\": {}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("Job type is required"));
    }

    @Test
    void outOfRangeFieldsReturn400() throws Exception {
        String token = registerAndLogin("job-ranges");

        submit(token, "{\"type\": \"demo.work\", \"priority\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.priority").exists());
        submit(token, "{\"type\": \"demo.work\", \"maxAttempts\": 11}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maxAttempts").exists());
        submit(token, "{\"type\": \"demo.work\", \"timeoutSeconds\": 3601}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timeoutSeconds").exists());
    }

    @Test
    void runAtAndDelayTogetherReturn400() throws Exception {
        String token = registerAndLogin("job-both-times");

        submit(token, """
                {"type": "demo.work", "runAt": "2030-01-01T00:00:00Z", "delaySeconds": 5}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void runAtMoreThanOneYearAheadReturns400() throws Exception {
        String token = registerAndLogin("job-too-far");
        String twoYears = Instant.now().plusSeconds(2L * 365 * 24 * 3600).toString();

        submit(token, "{\"type\": \"demo.work\", \"runAt\": \"" + twoYears + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        String token = registerAndLogin("job-bad-json");

        submit(token, "{\"type\": \"demo.work\", \"payload\": {oops").andExpect(status().isBadRequest());
    }

    @Test
    void oversizedPayloadReturns413AndStoresNothing() throws Exception {
        String token = registerAndLogin("job-big");
        String big = "{\"type\": \"demo.work\", \"payload\": {\"blob\": \"" + "x".repeat(70_000) + "\"}}";

        submit(token, big).andExpect(status().isPayloadTooLarge());

        assertThat(jobCountFor(token)).isZero();
    }

    @Test
    void payloadJustUnderTheLimitIsAccepted() throws Exception {
        String token = registerAndLogin("job-limit");
        String payloadValue = "x".repeat(65_000);

        submit(token, "{\"type\": \"demo.work\", \"payload\": {\"blob\": \"" + payloadValue + "\"}}")
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(DEMO_JOB))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/jobs/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/jobs/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- get ----------

    @Test
    void getReturnsJobWithAttemptsInOrder() throws Exception {
        String token = registerAndLogin("job-attempts");
        String id = submitOk(token, DEMO_JOB);
        UUID jobId = UUID.fromString(id);
        Instant t = Instant.parse("2026-12-01T10:00:00Z");

        // F1 never creates attempts; these rows stand in for what workers write from F3.
        attemptRepository.save(new JobAttempt(jobId, 2, "worker-b", t.plusSeconds(60), t.plusSeconds(61),
                JobAttemptOutcome.SUCCEEDED, null, 1000L));
        attemptRepository.save(new JobAttempt(jobId, 1, "worker-a", t, t.plusSeconds(5),
                JobAttemptOutcome.FAILED, "connection refused", 5000L));

        getJob(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.attempts.length()").value(2))
                .andExpect(jsonPath("$.attempts[0].attemptNo").value(1))
                .andExpect(jsonPath("$.attempts[0].outcome").value("FAILED"))
                .andExpect(jsonPath("$.attempts[0].error").value("connection refused"))
                .andExpect(jsonPath("$.attempts[0].workerId").value("worker-a"))
                .andExpect(jsonPath("$.attempts[1].attemptNo").value(2))
                .andExpect(jsonPath("$.attempts[1].outcome").value("SUCCEEDED"));
    }

    @Test
    void getJobWithNoAttemptsReturnsEmptyList() throws Exception {
        String token = registerAndLogin("job-no-attempts");
        String id = submitOk(token, DEMO_JOB);

        getJob(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(0));
    }

    @Test
    void getUnknownJobReturns404() throws Exception {
        String token = registerAndLogin("job-missing");

        getJob(token, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void getWithMalformedIdReturns400() throws Exception {
        String token = registerAndLogin("job-bad-id");

        getJob(token, "not-a-uuid").andExpect(status().isBadRequest());
    }

    // ---------- ownership ----------

    @Test
    void otherUsersGet404ForGetAndCancelAndTheJobIsUntouched() throws Exception {
        String owner = registerAndLogin("job-owner");
        String stranger = registerAndLogin("job-stranger");
        String id = submitOk(owner, DEMO_JOB);

        getJob(stranger, id).andExpect(status().isNotFound());
        cancelJob(stranger, id).andExpect(status().isNotFound());

        getJob(owner, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listShowsOnlyTheCallersJobs() throws Exception {
        String alice = registerAndLogin("job-alice");
        String bob = registerAndLogin("job-bob");
        String aliceJob = submitOk(alice, DEMO_JOB);
        submitOk(bob, DEMO_JOB);
        submitOk(bob, DEMO_JOB);

        mockMvc.perform(get("/api/v1/jobs").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(aliceJob));
        mockMvc.perform(get("/api/v1/jobs").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ---------- list ----------

    @Test
    void listPaginatesAndFilters() throws Exception {
        String token = registerAndLogin("job-list");
        String first = submitOk(token, DEMO_JOB);
        submitOk(token, DEMO_JOB);
        submitOk(token, DEMO_JOB);
        cancelJob(token, first).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/jobs?page=0&size=2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/v1/jobs?page=1&size=2").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/jobs?status=CANCELLED").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
        mockMvc.perform(get("/api/v1/jobs?status=PENDING").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/jobs?type=demo.work").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/v1/jobs?type=other.type").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listSortsByPriority() throws Exception {
        String token = registerAndLogin("job-sort");
        submitOk(token, "{\"type\": \"demo.work\", \"priority\": 2}");
        submitOk(token, "{\"type\": \"demo.work\", \"priority\": 9}");
        submitOk(token, "{\"type\": \"demo.work\", \"priority\": 5}");

        mockMvc.perform(get("/api/v1/jobs?sort=priority,desc").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content[0].priority").value(9))
                .andExpect(jsonPath("$.content[1].priority").value(5))
                .andExpect(jsonPath("$.content[2].priority").value(2));
    }

    @Test
    void invalidListParametersReturn400() throws Exception {
        String token = registerAndLogin("job-list-bad");
        String auth = "Bearer " + token;

        mockMvc.perform(get("/api/v1/jobs?size=0").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs?size=101").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs?page=-1").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs?sort=payload,asc").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs?status=NOT_A_STATUS").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    // ---------- cancel ----------

    @Test
    void cancelMovesPendingJobToCancelled() throws Exception {
        String token = registerAndLogin("job-cancel");
        String id = submitOk(token, DEMO_JOB);

        cancelJob(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.finishedAt").exists());

        getJob(token, id).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingTwiceSucceedsAndChangesNothing() throws Exception {
        String token = registerAndLogin("job-cancel-twice");
        String id = submitOk(token, DEMO_JOB);
        cancelJob(token, id).andExpect(status().isOk());
        Long versionAfterFirst = jdbc.queryForObject(
                "select version from jobs where id = ?::uuid", Long.class, id);

        cancelJob(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(jdbc.queryForObject("select version from jobs where id = ?::uuid", Long.class, id))
                .isEqualTo(versionAfterFirst);
    }

    @Test
    void cancellingAFinishedJobReturns409() throws Exception {
        String token = registerAndLogin("job-cancel-done");
        String id = submitOk(token, DEMO_JOB);
        jdbc.update("update jobs set status = 'SUCCEEDED' where id = ?::uuid", id);

        cancelJob(token, id).andExpect(status().isConflict());

        getJob(token, id).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void cancellingARunningJobIsAllowed() throws Exception {
        String token = registerAndLogin("job-cancel-running");
        String id = submitOk(token, DEMO_JOB);
        jdbc.update("update jobs set status = 'RUNNING' where id = ?::uuid", id);

        cancelJob(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------- idempotency ----------

    @Test
    void sameIdempotencyKeyReturnsTheOriginalJob() throws Exception {
        String token = registerAndLogin("job-idem");
        String key = "order-" + UUID.randomUUID();

        String first = submit(token, DEMO_JOB, key)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = submit(token, "{\"type\": \"demo.work\", \"priority\": 1}", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(5))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(jobCountFor(token)).isEqualTo(1);
    }

    @Test
    void differentKeysCreateDifferentJobs() throws Exception {
        String token = registerAndLogin("job-idem-diff");

        submit(token, DEMO_JOB, "key-a").andExpect(status().isCreated());
        submit(token, DEMO_JOB, "key-b").andExpect(status().isCreated());

        assertThat(jobCountFor(token)).isEqualTo(2);
    }

    @Test
    void sameKeyFromAnotherUserCreatesItsOwnJob() throws Exception {
        String alice = registerAndLogin("job-idem-a");
        String bob = registerAndLogin("job-idem-b");

        String aliceJob = JsonPath.read(
                submit(alice, DEMO_JOB, "shared-key").andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
        String bobJob = JsonPath.read(
                submit(bob, DEMO_JOB, "shared-key").andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(bobJob).isNotEqualTo(aliceJob);
    }

    @Test
    void requestsWithoutAKeyAlwaysCreateNewJobs() throws Exception {
        String token = registerAndLogin("job-no-key");

        submit(token, DEMO_JOB).andExpect(status().isCreated());
        submit(token, DEMO_JOB).andExpect(status().isCreated());

        assertThat(jobCountFor(token)).isEqualTo(2);
    }

    @Test
    void overlongIdempotencyKeyReturns400() throws Exception {
        String token = registerAndLogin("job-idem-long");

        submit(token, DEMO_JOB, "k".repeat(256)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneJob() throws Exception {
        String token = registerAndLogin("job-idem-race");
        String key = "race-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<Integer> call = () -> {
                start.await();
                return submit(token, DEMO_JOB, key).andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(call);
            Future<Integer> b = pool.submit(call);
            start.countDown();

            List<Integer> statuses = new ArrayList<>(List.of(a.get(), b.get()));
            Collections.sort(statuses);

            assertThat(statuses).containsExactly(200, 201);
            assertThat(jobCountFor(token)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- audit ----------

    @Test
    void submitAndCancelAreAuditedWithoutThePayload() throws Exception {
        String token = registerAndLogin("job-audit");
        String response = submit(token, """
                {"type": "demo.work", "payload": {"email": "secret-person@example.com"}}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");
        cancelJob(token, id).andExpect(status().isOk());

        Long ownerId = jdbc.queryForObject("select owner_id from jobs where id = ?::uuid", Long.class, id);
        List<String> actions = jdbc.queryForList(
                "select action from audit_logs where user_id = ? and resource_type = 'JOB' order by id",
                String.class, ownerId);
        List<String> changes = jdbc.queryForList(
                "select changes::text from audit_logs where user_id = ? and resource_type = 'JOB'",
                String.class, ownerId);

        assertThat(actions).containsExactly("JOB_SUBMITTED", "JOB_CANCELLED");
        assertThat(changes).allSatisfy(text -> {
            assertThat(text).contains(id);
            assertThat(text).doesNotContain("secret-person@example.com");
        });
    }

    @Test
    void anIdempotentReplayIsNotAuditedAgain() throws Exception {
        String token = registerAndLogin("job-audit-replay");
        String response = submit(token, DEMO_JOB, "audit-key").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        submit(token, DEMO_JOB, "audit-key").andExpect(status().isOk());
        String id = JsonPath.read(response, "$.id");

        Long ownerId = jdbc.queryForObject("select owner_id from jobs where id = ?::uuid", Long.class, id);

        assertThat(jdbc.queryForObject(
                "select count(*) from audit_logs where user_id = ? and action = 'JOB_SUBMITTED'",
                Long.class, ownerId)).isEqualTo(1);
    }

    // ---------- database ----------

    @Test
    void staleUpdateFailsOptimisticLock() throws Exception {
        String token = registerAndLogin("job-lock");
        UUID id = UUID.fromString(submitOk(token, DEMO_JOB));

        Job copyA = jobRepository.findById(id).orElseThrow();
        Job copyB = jobRepository.findById(id).orElseThrow();

        stateMachine.transition(copyA, JobStatus.CANCELLED, clock.instant());
        jobRepository.save(copyA);

        stateMachine.transition(copyB, JobStatus.QUEUED, clock.instant());
        assertThatThrownBy(() -> jobRepository.save(copyB))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(jobRepository.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void databaseRejectsAnUnknownStatus() {
        Long anyUser = jdbc.queryForObject("select min(id) from users", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into jobs (id, owner_id, type, status, run_at, created_at, updated_at)
                values (?::uuid, ?, 'demo.work', 'EXPLODED', now(), now(), now())
                """, UUID.randomUUID().toString(), anyUser))
                .hasMessageContaining("chk_jobs_status");
    }

    private long jobCountFor(String token) {
        Long ownerId = jdbc.queryForObject(
                "select id from users where username = ?", Long.class, usernameOf(token));
        return jdbc.queryForObject("select count(*) from jobs where owner_id = ?", Long.class, ownerId);
    }

    /** The JWT subject is the username, so it is the middle part of the token, base64url-encoded. */
    private String usernameOf(String token) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return JsonPath.read(payload, "$.sub");
    }
}
