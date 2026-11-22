package com.mkalki.cloudtaskapi.job;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "cloudtask.jobs.max-active-per-user=2")
class JobActiveLimitIntegrationTest extends JobApiTestSupport {

    @Autowired
    private ActiveJobLock activeJobLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void submittingBeyondTheActiveLimitReturns429() throws Exception {
        String token = registerAndLogin("job-cap");
        submit(token, DEMO_JOB).andExpect(status().isCreated());
        submit(token, DEMO_JOB).andExpect(status().isCreated());

        submit(token, DEMO_JOB)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Active job limit of 2 reached"));
    }

    @Test
    void cancellingAJobFreesCapacity() throws Exception {
        String token = registerAndLogin("job-cap-free");
        String first = submitOk(token, DEMO_JOB);
        submitOk(token, DEMO_JOB);
        submit(token, DEMO_JOB).andExpect(status().isTooManyRequests());

        cancelJob(token, first).andExpect(status().isOk());

        submit(token, DEMO_JOB).andExpect(status().isCreated());
    }

    @Test
    void theLimitIsPerUser() throws Exception {
        String alice = registerAndLogin("job-cap-a");
        String bob = registerAndLogin("job-cap-b");
        submitOk(alice, DEMO_JOB);
        submitOk(alice, DEMO_JOB);
        submit(alice, DEMO_JOB).andExpect(status().isTooManyRequests());

        submit(bob, DEMO_JOB).andExpect(status().isCreated());
    }

    @Test
    void replayingAnIdempotencyKeyStillWorksAtTheLimit() throws Exception {
        String token = registerAndLogin("job-cap-idem");
        submit(token, DEMO_JOB, "same").andExpect(status().isCreated());
        submitOk(token, DEMO_JOB);

        submit(token, DEMO_JOB, "same").andExpect(status().isOk());
    }

    @Test
    void parallelSubmitsAtTheLimitCreateExactlyOneJob() throws Exception {
        String token = registerAndLogin("job-cap-race");
        submitOk(token, DEMO_JOB); // limit is 2, so one slot is left
        int requests = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return submit(token, DEMO_JOB).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(Collections.frequency(statuses, 201)).as("statuses %s", statuses).isEqualTo(1);
            assertThat(Collections.frequency(statuses, 429)).as("statuses %s", statuses).isEqualTo(requests - 1);
            assertThat(jobCountFor(token)).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void retryWithTheSameKeyAtTheCapReturnsTheOriginalJobAndAnotherKeyGets429() throws Exception {
        String token = registerAndLogin("job-cap-idem-retry");
        String key = "K-" + UUID.randomUUID();
        String first = submit(token, DEMO_JOB, key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        submitOk(token, DEMO_JOB); // now 2 of 2 active jobs

        String retry = submit(token, DEMO_JOB, key).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(retry, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        submit(token, DEMO_JOB, "other-" + UUID.randomUUID()).andExpect(status().isTooManyRequests());
        assertThat(jobCountFor(token)).isEqualTo(2);
    }

    /**
     * The case the pre-check in submit() cannot catch: the retry starts while the first request
     * is still in flight. The retry passes the pre-check (nothing is committed yet) and waits
     * for the advisory lock. The test holds that lock, inserts the job for the key (so the user
     * is now at the cap) and then commits. Without the lookup after the lock, the retry would
     * count 2 of 2 active jobs and return 429.
     */
    @Test
    void retryThatWaitsForTheLockGetsTheJobCommittedMeanwhileNot429() throws Exception {
        String token = registerAndLogin("job-cap-idem-wait");
        long ownerId = userIdOf(token);
        submitOk(token, DEMO_JOB); // limit is 2, so one slot is left
        String key = "wait-" + UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> winner = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                activeJobLock.lockFor(ownerId);
                jdbc.update("""
                        insert into jobs (id, owner_id, type, status, run_at, idempotency_key, created_at, updated_at)
                        values (?, ?, 'demo.work', 'PENDING', now(), ?, now(), now())
                        """, winnerId, ownerId, key);
                lockHeld.countDown();
                awaitQuietly(release);
            }));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MockHttpServletResponse> retry = pool.submit(() ->
                    submit(token, DEMO_JOB, key).andReturn().getResponse());
            await().atMost(Duration.ofSeconds(10)).until(this::someSessionIsWaitingOnALock);
            release.countDown();

            winner.get(10, TimeUnit.SECONDS);
            MockHttpServletResponse response = retry.get(10, TimeUnit.SECONDS);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat((String) JsonPath.read(response.getContentAsString(), "$.id"))
                    .isEqualTo(winnerId.toString());
            assertThat(jobCountFor(token)).isEqualTo(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void theLockRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> activeJobLock.lockFor(1L))
                .isInstanceOf(IllegalStateException.class);

        transactionTemplate.executeWithoutResult(status -> activeJobLock.lockFor(1L));
        // An owner id above 2^31 is accepted by PostgreSQL (the bigint form takes the whole id).
        transactionTemplate.executeWithoutResult(status -> activeJobLock.lockFor(3_000_000_000L));
        transactionTemplate.executeWithoutResult(status -> activeJobLock.lockFor(Long.MAX_VALUE));
    }

    @Test
    void exactlyTheActiveStatusesCountTowardTheCap() throws Exception {
        String token = registerAndLogin("job-cap-statuses");
        long ownerId = userIdOf(token);
        for (JobStatus status : JobStatus.values()) {
            jdbc.update("""
                    insert into jobs (id, owner_id, type, status, run_at, created_at, updated_at)
                    values (?, ?, 'demo.work', ?, now(), now(), now())
                    """, UUID.randomUUID(), ownerId, status.name());
        }

        assertThat(jobRepository.countActiveByOwnerId(ownerId))
                .as("the native query must list the same statuses as JobStatus.activeStatuses()")
                .isEqualTo(JobStatus.activeStatuses().size());
    }

    @Test
    void activeJobIndexesArePartial() {
        List<String> definitions = jdbc.queryForList("""
                select indexdef from pg_indexes
                 where tablename = 'jobs'
                   and indexname in ('idx_jobs_owner_active', 'idx_jobs_waiting_run_at')
                """, String.class);

        assertThat(definitions).hasSize(2).allSatisfy(def -> assertThat(def).contains("WHERE"));
    }
}
