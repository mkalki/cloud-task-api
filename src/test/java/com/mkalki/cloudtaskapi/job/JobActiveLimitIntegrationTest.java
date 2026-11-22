package com.mkalki.cloudtaskapi.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

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
    void theLockRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> activeJobLock.lockFor(1L))
                .isInstanceOf(IllegalStateException.class);

        transactionTemplate.executeWithoutResult(status -> activeJobLock.lockFor(1L));
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
