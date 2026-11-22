package com.mkalki.cloudtaskapi.scheduler;

import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import com.mkalki.cloudtaskapi.queue.QueueService;
import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Lua scripts against a real Redis. */
@SpringBootTest
class QueueServiceIntegrationTest extends IntegrationTestConfig {

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

    @Test
    void promoteMovesOnlyJobsThatAreDue() {
        UUID due = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        queue.enqueueScheduled(due, 5, 1_000);
        queue.enqueueScheduled(later, 5, 2_000);

        assertThat(queue.promoteDue(999, 10)).isEmpty();
        assertThat(queue.promoteDue(1_000, 10)).containsExactly(due);

        assertThat(ready()).containsExactly(due.toString());
        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isEqualTo(1);
    }

    @Test
    void promoteStopsAtTheLimit() {
        for (int i = 0; i < 5; i++) {
            queue.enqueueScheduled(UUID.randomUUID(), 5, 1_000 + i);
        }

        assertThat(queue.promoteDue(10_000, 3)).hasSize(3);
        assertThat(queue.promoteDue(10_000, 3)).hasSize(2);
        assertThat(queue.promoteDue(10_000, 3)).isEmpty();
    }

    @Test
    void readyScoreMatchesTheJavaFormulaForEveryPriority() {
        for (int priority = 0; priority <= 9; priority++) {
            UUID id = UUID.randomUUID();
            queue.enqueueScheduled(id, priority, 1_785_000_000_123L);
            queue.promoteDue(1_785_000_000_123L, 10);

            Double score = redis.opsForZSet().score(RedisKeys.READY, id.toString());
            assertThat(score.longValue())
                    .as("priority %d", priority)
                    .isEqualTo(RedisKeys.readyScore(priority, 1_785_000_000_123L));
        }
    }

    @Test
    void higherPriorityIsServedFirstThenOldestFirst() {
        UUID low = UUID.randomUUID();
        UUID highFirst = UUID.randomUUID();
        UUID highSecond = UUID.randomUUID();
        UUID medium = UUID.randomUUID();
        queue.enqueueScheduled(low, 0, 1_000);
        queue.enqueueScheduled(highFirst, 9, 1_001);
        queue.enqueueScheduled(medium, 5, 1_002);
        queue.enqueueScheduled(highSecond, 9, 1_003);

        queue.promoteDue(5_000, 10);

        assertThat(ready()).containsExactly(
                highFirst.toString(), highSecond.toString(), medium.toString(), low.toString());
    }

    @Test
    void aJobHeldByAWorkerIsNotPutBackInReady() {
        UUID held = UUID.randomUUID();
        redis.opsForZSet().add(RedisKeys.PROCESSING, held.toString(), 99_999);
        queue.enqueueScheduled(held, 5, 1_000);

        assertThat(queue.promoteDue(2_000, 10)).isEmpty();

        assertThat(ready()).isEmpty();
        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isZero();
    }

    @Test
    void aMalformedMemberIsDroppedAndDoesNotBlockTheQueue() {
        UUID good = UUID.randomUUID();
        redis.opsForZSet().add(RedisKeys.SCHEDULED, "garbage-without-separator", 500);
        redis.opsForZSet().add(RedisKeys.SCHEDULED, "x:" + UUID.randomUUID(), 600);
        queue.enqueueScheduled(good, 5, 1_000);

        assertThat(queue.promoteDue(2_000, 10)).containsExactly(good);

        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isZero();
    }

    @Test
    void promotingTwiceKeepsTheOriginalReadyPosition() {
        UUID id = UUID.randomUUID();
        queue.enqueueScheduled(id, 5, 1_000);
        queue.promoteDue(2_000, 10);
        Double first = redis.opsForZSet().score(RedisKeys.READY, id.toString());

        queue.enqueueScheduled(id, 5, 1_900);
        queue.promoteDue(2_000, 10);

        assertThat(redis.opsForZSet().score(RedisKeys.READY, id.toString())).isEqualTo(first);
        assertThat(redis.opsForZSet().zCard(RedisKeys.READY)).isEqualTo(1);
    }

    @Test
    void enqueueScheduledIsIdempotent() {
        UUID id = UUID.randomUUID();

        queue.enqueueScheduled(id, 5, 1_000);
        queue.enqueueScheduled(id, 5, 1_000);

        assertThat(redis.opsForZSet().zCard(RedisKeys.SCHEDULED)).isEqualTo(1);
    }

    @Test
    void enqueueReadyAddsOnceAndSkipsJobsHeldByAWorker() {
        UUID id = UUID.randomUUID();
        UUID held = UUID.randomUUID();
        redis.opsForZSet().add(RedisKeys.PROCESSING, held.toString(), 99_999);

        assertThat(queue.enqueueReady(id, 5, 1_000)).isTrue();
        assertThat(queue.enqueueReady(id, 5, 1_000)).isFalse();
        assertThat(queue.enqueueReady(held, 5, 1_000)).isFalse();

        assertThat(ready()).containsExactly(id.toString());
    }

    @Test
    void removeDeletesTheJobFromScheduledAndReadyWhateverItsPriority() {
        UUID scheduled = UUID.randomUUID();
        UUID inReady = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        queue.enqueueScheduled(scheduled, 3, 1_000_000);
        queue.enqueueScheduled(other, 3, 1_000_000);
        queue.enqueueReady(inReady, 8, 1_000);

        queue.remove(scheduled);
        queue.remove(inReady);

        assertThat(redis.opsForZSet().range(RedisKeys.SCHEDULED, 0, -1))
                .containsExactly(RedisKeys.scheduledMember(3, other));
        assertThat(ready()).isEmpty();
    }

    private List<String> ready() {
        return List.copyOf(redis.opsForZSet().range(RedisKeys.READY, 0, -1));
    }
}
