package com.mkalki.cloudtaskapi.queue;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only class that touches the job queues in Redis. Redis is an accelerator:
 * PostgreSQL stays the source of truth, so a failure here is repaired by the reconciler.
 * Every multi-step operation is one Lua script, so no other client can interleave.
 */
@Service
public class QueueService {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> PROMOTE_DUE = script("promote_due.lua", List.class);
    private static final DefaultRedisScript<Long> ENQUEUE_READY = script("enqueue_ready.lua", Long.class);
    private static final DefaultRedisScript<Long> REMOVE = script("remove.lua", Long.class);
    private static final DefaultRedisScript<String> CLAIM = script("claim.lua", String.class);
    private static final DefaultRedisScript<Long> RENEW_LEASE = script("renew_lease.lua", Long.class);

    private final StringRedisTemplate redis;

    public QueueService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Adds the job to jobs:scheduled. Repeating the call with the same values changes nothing. */
    public void enqueueScheduled(UUID jobId, int priority, long runAtMs) {
        redis.opsForZSet().add(RedisKeys.SCHEDULED, RedisKeys.scheduledMember(priority, jobId), runAtMs);
    }

    /**
     * Adds the job to jobs:ready unless it is already ready or held by a worker.
     *
     * @param dueMs the time the job became runnable; it orders jobs of the same priority
     * @return true if the job was added
     */
    public boolean enqueueReady(UUID jobId, int priority, long dueMs) {
        Long added = redis.execute(
                ENQUEUE_READY,
                List.of(RedisKeys.READY, RedisKeys.PROCESSING),
                jobId.toString(),
                Long.toString(RedisKeys.readyScore(priority, dueMs)));
        return added != null && added == 1;
    }

    /** Best-effort removal from the scheduled and ready queues. Jobs held by a worker are untouched. */
    public void remove(UUID jobId) {
        redis.execute(REMOVE, List.of(RedisKeys.SCHEDULED, RedisKeys.READY), jobId.toString());
    }

    /**
     * Atomically takes the best job from jobs:ready and registers it in jobs:processing with the
     * given lease deadline. Each job is returned to exactly one caller.
     *
     * @param leaseDeadlineMs epoch milliseconds at which the claim expires unless it is renewed
     * @return the job id, or empty when nothing is ready
     */
    public Optional<UUID> claim(long leaseDeadlineMs) {
        String id = redis.execute(
                CLAIM,
                List.of(RedisKeys.READY, RedisKeys.PROCESSING),
                Long.toString(leaseDeadlineMs));
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    /**
     * Moves the lease deadline of a job that is still in jobs:processing. It never adds the job:
     * false means the job is not held any more (released, or Redis lost it) and the caller lost its lease.
     */
    public boolean renewLease(UUID jobId, long leaseDeadlineMs) {
        Long renewed = redis.execute(
                RENEW_LEASE,
                List.of(RedisKeys.PROCESSING),
                jobId.toString(),
                Long.toString(leaseDeadlineMs));
        return renewed != null && renewed == 1;
    }

    /** Removes the job from jobs:processing. Calling it for a job that is not there changes nothing. */
    public void release(UUID jobId) {
        redis.opsForZSet().remove(RedisKeys.PROCESSING, jobId.toString());
    }

    /**
     * Atomically moves up to limit jobs whose run-at time is at or before nowMs from scheduled
     * to ready. Each job is returned to exactly one caller, even when several scheduler
     * instances call this at the same moment.
     */
    public List<UUID> promoteDue(long nowMs, int limit) {
        List<?> ids = redis.execute(
                PROMOTE_DUE,
                List.of(RedisKeys.SCHEDULED, RedisKeys.READY, RedisKeys.PROCESSING),
                Long.toString(nowMs),
                Integer.toString(limit));
        if (ids == null) {
            return List.of();
        }
        return ids.stream().map(id -> UUID.fromString(id.toString())).toList();
    }

    private static <T> DefaultRedisScript<T> script(String file, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/" + file));
        script.setResultType(resultType);
        return script;
    }
}
