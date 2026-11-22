package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import com.mkalki.cloudtaskapi.queue.QueueService;
import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** claim.lua and renew_lease.lua against a real Redis. */
@SpringBootTest
class ClaimScriptIntegrationTest extends IntegrationTestConfig {

    @Autowired
    private QueueService queue;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void flushRedis() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    // ---------- claim ----------

    @Test
    void claimOnAnEmptyQueueReturnsNothingAndWritesNothing() {
        assertThat(queue.claim(60_000)).isEmpty();

        assertThat(redis.opsForZSet().zCard(RedisKeys.PROCESSING)).isZero();
    }

    @Test
    void claimTakesTheBestJobAndRegistersItWithTheLeaseDeadline() {
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        queue.enqueueReady(low, 0, 1_000);
        queue.enqueueReady(high, 9, 2_000);

        Optional<UUID> claimed = queue.claim(60_000);

        assertThat(claimed).contains(high);
        assertThat(redis.opsForZSet().score(RedisKeys.PROCESSING, high.toString())).isEqualTo(60_000.0);
        assertThat(redis.opsForZSet().score(RedisKeys.READY, high.toString())).isNull();
        assertThat(redis.opsForZSet().score(RedisKeys.READY, low.toString())).isNotNull();
    }

    @Test
    void claimServesEqualPrioritiesFirstInFirstOut() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        queue.enqueueReady(second, 5, 2_000);
        queue.enqueueReady(first, 5, 1_000);

        assertThat(queue.claim(60_000)).contains(first);
        assertThat(queue.claim(60_000)).contains(second);
        assertThat(queue.claim(60_000)).isEmpty();
    }

    @Test
    void claimReturnsTheBareIdEvenIfAPriorityMemberReachedTheReadyQueue() {
        UUID id = UUID.randomUUID();
        redis.opsForZSet().add(RedisKeys.READY, "5:" + id, 1);

        assertThat(queue.claim(60_000)).contains(id);

        assertThat(redis.opsForZSet().score(RedisKeys.PROCESSING, id.toString())).isNotNull();
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isZero();
    }

    @Test
    void everyJobGoesToExactlyOneOfManyConcurrentClaimers() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            queue.enqueueReady(id, i % 10, 1_000 + i);
        }

        int claimers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(claimers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<UUID>>> futures = new ArrayList<>();
        for (int t = 0; t < claimers; t++) {
            Callable<List<UUID>> claimer = () -> {
                go.await();
                List<UUID> mine = new ArrayList<>();
                Optional<UUID> next;
                while ((next = queue.claim(60_000)).isPresent()) {
                    mine.add(next.get());
                }
                return mine;
            };
            futures.add(pool.submit(claimer));
        }
        go.countDown();

        List<UUID> all = new ArrayList<>();
        for (Future<List<UUID>> future : futures) {
            all.addAll(future.get());
        }
        pool.shutdown();

        assertThat(all).hasSize(60).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids);
        assertThat(redis.opsForZSet().zCard(RedisKeys.PROCESSING)).isEqualTo(60);
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isZero();
    }

    // ---------- renew ----------

    @Test
    void renewReturnsTrueAndMovesTheDeadlineForAHeldJob() {
        UUID id = UUID.randomUUID();
        queue.enqueueReady(id, 5, 1_000);
        queue.claim(10_000);

        assertThat(queue.renewLease(id, 90_000)).isTrue();

        assertThat(redis.opsForZSet().score(RedisKeys.PROCESSING, id.toString())).isEqualTo(90_000.0);
    }

    @Test
    void renewReturnsFalseForAnUnknownMemberAndAddsNothing() {
        assertThat(queue.renewLease(UUID.randomUUID(), 90_000)).isFalse();

        assertThat(redis.opsForZSet().zCard(RedisKeys.PROCESSING)).isZero();
    }

    @Test
    void renewNeverBringsBackAReleasedJob() {
        UUID id = UUID.randomUUID();
        queue.enqueueReady(id, 5, 1_000);
        queue.claim(10_000);
        queue.release(id);

        assertThat(queue.renewLease(id, 90_000)).isFalse();

        assertThat(redis.opsForZSet().score(RedisKeys.PROCESSING, id.toString())).isNull();
    }

    @Test
    void releasingAJobThatIsNotHeldChangesNothing() {
        UUID held = UUID.randomUUID();
        queue.enqueueReady(held, 5, 1_000);
        queue.claim(10_000);

        queue.release(UUID.randomUUID());

        assertThat(redis.opsForZSet().zCard(RedisKeys.PROCESSING)).isEqualTo(1);
    }
}
