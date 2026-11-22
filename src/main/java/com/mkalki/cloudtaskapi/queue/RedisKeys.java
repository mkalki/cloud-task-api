package com.mkalki.cloudtaskapi.queue;

import java.util.UUID;

/**
 * Every Redis key name and score formula in one place. The Lua scripts in
 * src/main/resources/redis repeat the score formula; a test checks that both agree.
 */
public final class RedisKeys {

    /** ZSET. Member "priority:jobId", score = run-at epoch milliseconds. */
    public static final String SCHEDULED = "jobs:scheduled";

    /** ZSET. Member jobId, score = (9 - priority) * 1e14 + due epoch milliseconds. */
    public static final String READY = "jobs:ready";

    /** ZSET. Member jobId, score = lease deadline in epoch milliseconds (written from F3 on). */
    public static final String PROCESSING = "jobs:processing";

    public static final int MAX_PRIORITY = 9;

    /** 10^14 is larger than any epoch-millisecond value, and 9 * 10^14 is below 2^53. */
    public static final long PRIORITY_STEP = 100_000_000_000_000L;

    private RedisKeys() {
    }

    public static String scheduledMember(int priority, UUID jobId) {
        return priority + ":" + jobId;
    }

    /** A lower score is served first: higher priority wins, then the earlier due time. */
    public static long readyScore(int priority, long dueEpochMs) {
        return (MAX_PRIORITY - priority) * PRIORITY_STEP + dueEpochMs;
    }

    /**
     * Counter keys written by the demo.work handler when a job has a counterKey (used by tests and
     * demos to prove that a job never runs twice at once). The owner id keeps users apart.
     *
     * @param field one of running, runs, overlaps
     */
    public static String demoCounter(long ownerId, String counterKey, String field) {
        return "demo:counter:" + ownerId + ":" + counterKey + ":" + field;
    }
}
