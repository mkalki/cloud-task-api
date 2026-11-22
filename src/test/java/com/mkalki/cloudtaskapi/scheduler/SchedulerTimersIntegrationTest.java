package com.mkalki.cloudtaskapi.scheduler;

import com.mkalki.cloudtaskapi.job.JobApiTestSupport;
import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The real timers and real clock, with no profile set (so the default api,scheduler applies).
 * The context is closed afterwards so its timers cannot touch other tests' jobs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "cloudtask.scheduler.enabled=true",
        "cloudtask.scheduler.poll-interval=PT0.1S",
        "cloudtask.scheduler.reconcile-interval=PT1S"
})
class SchedulerTimersIntegrationTest extends JobApiTestSupport {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void aSubmittedJobBecomesQueuedWithoutAnyoneCallingThePromoter() throws Exception {
        String token = registerAndLogin("timer");
        UUID id = UUID.fromString(submitOk(token, DEMO_JOB));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select status from jobs where id = ?", String.class, id))
                        .isEqualTo("QUEUED"));
        assertThat(redis.opsForZSet().score(RedisKeys.READY, id.toString())).isNotNull();
    }

    @Test
    void thePeriodicReconcilerRefillsRedisAfterAFlush() throws Exception {
        String token = registerAndLogin("timer-flush");
        UUID id = UUID.fromString(submitOk(token, """
                {"type": "demo.work", "delaySeconds": 3600}
                """));
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });

        String member = RedisKeys.scheduledMember(5, id);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(redis.opsForZSet().score(RedisKeys.SCHEDULED, member)).isNotNull());
    }
}
