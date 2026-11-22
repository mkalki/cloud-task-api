package com.mkalki.cloudtaskapi.job.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkalki.cloudtaskapi.job.exception.InvalidJobRequestException;
import com.mkalki.cloudtaskapi.queue.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Demo and test handler. Payload (all fields optional, unknown fields are ignored):
 * <ul>
 *   <li>{@code durationMs}: how long to sleep, 0 to 60000 (default 0)</li>
 *   <li>{@code failProbability}: chance from 0 to 1 that the run throws after sleeping (default 0)</li>
 *   <li>{@code counterKey}: tests only. Counts runs and overlaps in Redis, see {@link RedisKeys#demoCounter}</li>
 * </ul>
 */
@Component
public class DemoWorkHandler implements JobHandler {

    public static final String TYPE = "demo.work";
    public static final int MAX_DURATION_MS = 60_000;

    private static final Pattern COUNTER_KEY = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Duration COUNTER_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public DemoWorkHandler(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void validatePayload(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new InvalidJobRequestException("demo.work payload must be a JSON object");
        }

        JsonNode duration = payload.get("durationMs");
        if (present(duration)
                && !(duration.isIntegralNumber() && duration.canConvertToInt()
                && duration.asInt() >= 0 && duration.asInt() <= MAX_DURATION_MS)) {
            throw new InvalidJobRequestException(
                    "demo.work durationMs must be a whole number from 0 to " + MAX_DURATION_MS);
        }

        JsonNode probability = payload.get("failProbability");
        if (present(probability)
                && !(probability.isNumber() && probability.asDouble() >= 0 && probability.asDouble() <= 1)) {
            throw new InvalidJobRequestException("demo.work failProbability must be a number from 0 to 1");
        }

        JsonNode counterKey = payload.get("counterKey");
        if (present(counterKey)
                && !(counterKey.isTextual() && COUNTER_KEY.matcher(counterKey.asText()).matches())) {
            throw new InvalidJobRequestException(
                    "demo.work counterKey must be 1 to 64 characters from A-Z a-z 0-9 . _ -");
        }
    }

    @Override
    public void handle(JobContext ctx) {
        if (ctx.isCancelled()) {
            return;
        }

        JsonNode payload = ctx.payload();
        int durationMs = payload.path("durationMs").asInt(0);
        double failProbability = payload.path("failProbability").asDouble(0);
        String counterKey = payload.path("counterKey").isTextual() ? payload.path("counterKey").asText() : null;

        if (counterKey != null) {
            enter(ctx.ownerId(), counterKey);
        }
        try {
            sleep(durationMs);
            if (failProbability > 0 && ThreadLocalRandom.current().nextDouble() < failProbability) {
                throw new IllegalStateException("demo.work failed on purpose (failProbability=" + failProbability + ")");
            }
        } finally {
            if (counterKey != null) {
                leave(ctx.ownerId(), counterKey);
            }
        }
    }

    private static boolean present(JsonNode node) {
        return node != null && !node.isNull();
    }

    private static void sleep(int durationMs) {
        if (durationMs <= 0) {
            return;
        }
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("demo.work was interrupted");
        }
    }

    /** runs counts every start. overlaps counts a start while another run of the same key is active. */
    private void enter(long ownerId, String counterKey) {
        String runs = RedisKeys.demoCounter(ownerId, counterKey, "runs");
        String running = RedisKeys.demoCounter(ownerId, counterKey, "running");

        redis.opsForValue().increment(runs);
        redis.expire(runs, COUNTER_TTL);

        Long active = redis.opsForValue().increment(running);
        redis.expire(running, COUNTER_TTL);
        if (active != null && active > 1) {
            String overlaps = RedisKeys.demoCounter(ownerId, counterKey, "overlaps");
            redis.opsForValue().increment(overlaps);
            redis.expire(overlaps, COUNTER_TTL);
        }
    }

    private void leave(long ownerId, String counterKey) {
        redis.opsForValue().decrement(RedisKeys.demoCounter(ownerId, counterKey, "running"));
    }
}
