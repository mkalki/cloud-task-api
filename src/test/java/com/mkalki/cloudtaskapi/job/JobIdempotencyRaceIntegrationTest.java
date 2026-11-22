package com.mkalki.cloudtaskapi.job;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Forces the duplicate-key path of {@code JobService.submit} instead of hoping two threads overlap.
 *
 * <p>A "rival" transaction inserts a job with the same (owner, Idempotency-Key) and does not
 * commit. The HTTP submit then misses the key in its pre-check (the row is not committed), and
 * its insert blocks on the unique index. Once we see it waiting, the rival commits or rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobIdempotencyRaceIntegrationTest extends JobApiTestSupport {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void insertThatLosesOnTheUniqueIndexReturnsTheWinnersJob() throws Exception {
        String token = registerAndLogin("job-idem-fallback");
        String key = "fallback-" + UUID.randomUUID();
        UUID rivalId = UUID.randomUUID();

        MockHttpServletResponse response = submitWhileRivalHoldsTheKey(token, key, rivalId, true);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.id")).isEqualTo(rivalId.toString());
        assertThat(jobCountFor(token)).isEqualTo(1);
        assertThat(submittedAuditRows(token)).as("the losing insert was rolled back with its audit row").isZero();
    }

    @Test
    void insertProceedsWhenTheRivalRollsBack() throws Exception {
        String token = registerAndLogin("job-idem-rollback");
        String key = "rollback-" + UUID.randomUUID();
        UUID rivalId = UUID.randomUUID();

        MockHttpServletResponse response = submitWhileRivalHoldsTheKey(token, key, rivalId, false);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.id")).isNotEqualTo(rivalId.toString());
        assertThat(jobCountFor(token)).isEqualTo(1);
    }

    private MockHttpServletResponse submitWhileRivalHoldsTheKey(
            String token, String key, UUID rivalId, boolean rivalCommits) throws Exception {
        long ownerId = userIdOf(token);
        CountDownLatch rivalInserted = new CountDownLatch(1);
        CountDownLatch finishRival = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> rival = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.update("""
                        insert into jobs (id, owner_id, type, status, run_at, idempotency_key, created_at, updated_at)
                        values (?, ?, 'demo.work', 'PENDING', now(), ?, now(), now())
                        """, rivalId, ownerId, key);
                rivalInserted.countDown();
                awaitQuietly(finishRival);
                if (!rivalCommits) {
                    status.setRollbackOnly();
                }
            }));
            assertThat(rivalInserted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MockHttpServletResponse> submit = pool.submit(() ->
                    submit(token, DEMO_JOB, key).andReturn().getResponse());

            // The submit's insert is now blocked on the rival's uncommitted index entry.
            await().atMost(Duration.ofSeconds(10)).until(this::someSessionIsWaitingOnALock);
            finishRival.countDown();

            rival.get(10, TimeUnit.SECONDS);
            return submit.get(10, TimeUnit.SECONDS);
        } finally {
            finishRival.countDown();
            pool.shutdownNow();
        }
    }

    private long submittedAuditRows(String token) {
        return jdbc.queryForObject(
                "select count(*) from audit_logs where user_id = ? and action = 'JOB_SUBMITTED'",
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
