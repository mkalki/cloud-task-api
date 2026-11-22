package com.mkalki.cloudtaskapi.scheduler;

import com.mkalki.cloudtaskapi.job.JobApiTestSupport;
import com.mkalki.cloudtaskapi.queue.QueueService;
import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostgreSQL + Redis + the promoter and reconciler, with a clock the test moves by hand.
 * The scheduler timers are off (src/test/resources/application.properties): the tests call
 * promoteDue() and reconcile() themselves.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SchedulerIntegrationTest.TestClockConfig.class)
@TestPropertySource(properties = {
        "cloudtask.scheduler.promote-batch-size=7",
        "cloudtask.scheduler.reconcile-batch-size=2"
})
class SchedulerIntegrationTest extends JobApiTestSupport {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        }
    }

    @Autowired
    private MutableClock clock;

    @Autowired
    private JobPromoter promoter;

    @Autowired
    private QueueReconciler reconciler;

    @Autowired
    private QueueService queue;

    @Autowired
    private StringRedisTemplate redis;

    private String token;

    @BeforeEach
    void cleanSlate() throws Exception {
        flushRedis();
        jdbc.update("delete from jobs");
        token = registerAndLogin("sched");
    }

    // ---------- delay ----------

    @Test
    void aDelayedJobIsNotPromotedEarlyAndIsPromotedOnceAfterRunAt() throws Exception {
        UUID id = submitJob(5, 60);
        assertThat(inScheduled(id)).isTrue();

        clock.advance(Duration.ofSeconds(59));
        assertThat(promoter.promoteDue()).isEmpty();
        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(inScheduled(id)).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(promoter.promoteDue()).containsExactly(id);
        assertThat(statusOf(id)).isEqualTo("QUEUED");
        assertThat(inScheduled(id)).isFalse();
        assertThat(inReady(id)).isTrue();

        clock.advance(Duration.ofSeconds(30));
        assertThat(promoter.promoteDue()).isEmpty();
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isEqualTo(1);
        assertThat(versionOf(id)).isEqualTo(1);
    }

    @Test
    void anImmediateJobIsPromotedOnTheNextRun() throws Exception {
        UUID id = submitJob(5, 0);

        assertThat(promoter.promoteDue()).containsExactly(id);
        assertThat(statusOf(id)).isEqualTo("QUEUED");
    }

    // ---------- atomicity ----------

    @Test
    void threeSchedulersPollingAtOnceMoveEachJobExactlyOnce() throws Exception {
        List<UUID> submitted = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            submitted.add(submitJob(i % 10, 0));
            clock.advance(Duration.ofMillis(1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<UUID>>> futures = new ArrayList<>();
        for (int t = 0; t < 3; t++) {
            Callable<List<UUID>> poller = () -> {
                go.await();
                List<UUID> mine = new ArrayList<>();
                List<UUID> batch;
                do {
                    batch = promoter.promoteDue();
                    mine.addAll(batch);
                } while (!batch.isEmpty());
                return mine;
            };
            futures.add(pool.submit(poller));
        }
        go.countDown();

        List<UUID> all = new ArrayList<>();
        for (Future<List<UUID>> future : futures) {
            all.addAll(future.get());
        }
        pool.shutdown();

        assertThat(all).hasSize(40).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(submitted);
        for (UUID id : submitted) {
            assertThat(statusOf(id)).isEqualTo("QUEUED");
            assertThat(versionOf(id)).as("each row changed once").isEqualTo(1);
        }
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isEqualTo(40);
        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isZero();
    }

    @Test
    void aCrashBetweenTheRedisMoveAndTheDatabaseUpdateIsRepaired() throws Exception {
        UUID id = submitJob(5, 0);
        // Step 1 of a promote run happened, then the process died before the SQL update.
        assertThat(queue.promoteDue(clock.millis(), 10)).containsExactly(id);
        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(inReady(id)).isTrue();

        reconciler.reconcile();
        assertThat(inScheduled(id)).isTrue();

        assertThat(promoter.promoteDue()).containsExactly(id);
        assertThat(statusOf(id)).isEqualTo("QUEUED");
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isEqualTo(1);
        assertThat(inScheduled(id)).isFalse();
    }

    // ---------- priority ----------

    @Test
    void readyQueueServesPriority9Then5Then0AndIsFifoWithinAPriority() throws Exception {
        UUID low = submitJob(0, 0);
        clock.advance(Duration.ofMillis(1));
        UUID highFirst = submitJob(9, 0);
        clock.advance(Duration.ofMillis(1));
        UUID medium = submitJob(5, 0);
        clock.advance(Duration.ofMillis(1));
        UUID highSecond = submitJob(9, 0);
        clock.advance(Duration.ofMillis(1));

        assertThat(promoter.promoteDue()).hasSize(4);

        assertThat(readyOrder()).containsExactly(highFirst, highSecond, medium, low);
    }

    // ---------- cancel ----------

    @Test
    void aJobCancelledBeforeItIsDueIsNeverPromoted() throws Exception {
        UUID id = submitJob(5, 60);

        cancelJob(token, id.toString()).andExpect(status().isOk());
        assertThat(inScheduled(id)).isFalse();

        clock.advance(Duration.ofMinutes(5));
        assertThat(promoter.promoteDue()).isEmpty();
        reconciler.reconcile();
        assertThat(promoter.promoteDue()).isEmpty();

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(inReady(id)).isFalse();
        assertThat(inScheduled(id)).isFalse();
    }

    @Test
    void aCancelledJobThatIsStillInRedisIsDroppedNotQueued() throws Exception {
        UUID id = submitJob(5, 0);
        cancelJob(token, id.toString()).andExpect(status().isOk());
        // Simulates the best-effort Redis removal having failed.
        queue.enqueueScheduled(id, 5, clock.millis());

        assertThat(promoter.promoteDue()).isEmpty();

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(inReady(id)).as("the promoter removes the junk entry").isFalse();
    }

    @Test
    void cancellingAQueuedJobRemovesItFromReady() throws Exception {
        UUID id = submitJob(5, 0);
        promoter.promoteDue();
        assertThat(inReady(id)).isTrue();

        cancelJob(token, id.toString()).andExpect(status().isOk());

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(inReady(id)).isFalse();
    }

    // ---------- reconciler ----------

    @Test
    void afterFlushAllTheReconcilerRestoresBothQueuesAndNothingIsLost() throws Exception {
        List<UUID> stillPending = new ArrayList<>();
        List<UUID> queued = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stillPending.add(submitJob(i, 3600));
        }
        // Highest priority first, so the expected ready order equals the submit order.
        for (int i = 0; i < 5; i++) {
            queued.add(submitJob(9 - i, 0));
            clock.advance(Duration.ofMillis(1));
        }
        promoter.promoteDue();
        UUID cancelled = submitJob(5, 3600);
        cancelJob(token, cancelled.toString()).andExpect(status().isOk());
        for (UUID id : queued) {
            assertThat(statusOf(id)).isEqualTo("QUEUED");
        }

        flushRedis();
        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isZero();
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isZero();

        QueueReconciler.ReconcileResult result = reconciler.reconcile();

        assertThat(result.pendingEnsured()).isEqualTo(5);
        assertThat(result.queuedRestored()).isEqualTo(5);
        for (UUID id : stillPending) {
            assertThat(inScheduled(id)).isTrue();
        }
        assertThat(inScheduled(cancelled)).isFalse();
        assertThat(inReady(cancelled)).isFalse();
        assertThat(readyOrder()).containsExactlyElementsOf(queued);
    }

    @Test
    void runningTheReconcilerAgainChangesNothing() throws Exception {
        UUID pending = submitJob(5, 3600);
        UUID queued = submitJob(5, 0);
        promoter.promoteDue();
        String member = RedisKeys.scheduledMember(5, pending);
        Double scheduledScore = redis.opsForZSet().score(RedisKeys.SCHEDULED, member);
        Double readyScore = redis.opsForZSet().score(RedisKeys.READY, queued.toString());

        QueueReconciler.ReconcileResult second = reconciler.reconcile();

        assertThat(second.queuedRestored()).isZero();
        assertThat(redis.opsForZSet().score(RedisKeys.SCHEDULED, member)).isEqualTo(scheduledScore);
        assertThat(redis.opsForZSet().score(RedisKeys.READY, queued.toString())).isEqualTo(readyScore);
        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isEqualTo(1);
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isEqualTo(1);
    }

    @Test
    void theReconcilerLeavesAQueuedJobAloneWhileAWorkerHoldsIt() throws Exception {
        UUID id = submitJob(5, 0);
        promoter.promoteDue();
        // What a worker's claim will do in F3: move the id from ready to processing.
        redis.opsForZSet().remove(RedisKeys.READY, id.toString());
        redis.opsForZSet().add(RedisKeys.PROCESSING, id.toString(), clock.millis() + 30_000);

        QueueReconciler.ReconcileResult result = reconciler.reconcile();

        assertThat(result.queuedRestored()).isZero();
        assertThat(inReady(id)).isFalse();
    }

    @Test
    void severalReconcilersRunningTogetherLeaveOneEntryPerJob() throws Exception {
        for (int i = 0; i < 6; i++) {
            submitJob(i, i < 3 ? 3600 : 0);
        }
        promoter.promoteDue();
        flushRedis();

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 3; t++) {
            futures.add(pool.submit(() -> {
                go.await();
                return reconciler.reconcile();
            }));
        }
        go.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isEqualTo(3);
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isEqualTo(3);
    }

    // ---------- helpers ----------

    private UUID submitJob(int priority, int delaySeconds) throws Exception {
        String body = """
                {"type": "demo.work", "priority": %d, "delaySeconds": %d}
                """.formatted(priority, delaySeconds);
        return UUID.fromString(submitOk(token, body));
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("select status from jobs where id = ?", String.class, id);
    }

    private long versionOf(UUID id) {
        return jdbc.queryForObject("select version from jobs where id = ?", Long.class, id);
    }

    private boolean inScheduled(UUID id) {
        return redis.opsForZSet().range(RedisKeys.SCHEDULED, 0, -1)
                .stream().anyMatch(member -> member.endsWith(":" + id));
    }

    private boolean inReady(UUID id) {
        return redis.opsForZSet().score(RedisKeys.READY, id.toString()) != null;
    }

    private List<UUID> readyOrder() {
        List<UUID> order = new ArrayList<>();
        for (String member : redis.opsForZSet().range(RedisKeys.READY, 0, -1)) {
            order.add(UUID.fromString(member));
        }
        return order;
    }

    private void flushRedis() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}
