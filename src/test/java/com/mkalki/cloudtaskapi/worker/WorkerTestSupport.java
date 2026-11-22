package com.mkalki.cloudtaskapi.worker;

import com.mkalki.cloudtaskapi.job.JobApiTestSupport;
import com.mkalki.cloudtaskapi.queue.QueueService;
import com.mkalki.cloudtaskapi.queue.RedisKeys;
import com.mkalki.cloudtaskapi.scheduler.JobPromoter;
import com.mkalki.cloudtaskapi.scheduler.QueueReconciler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Helpers shared by the worker integration tests. Subclasses carry @WorkerTestContext. */
abstract class WorkerTestSupport extends JobApiTestSupport {

    protected static final Duration PATIENCE = Duration.ofSeconds(30);

    @Autowired
    protected WorkerFactory workerFactory;

    @Autowired
    protected JobPromoter promoter;

    @Autowired
    protected QueueReconciler reconciler;

    @Autowired
    protected QueueService queue;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected ControlledHandler controlled;

    protected String token;

    private final List<Worker> workers = new ArrayList<>();

    @BeforeEach
    void cleanSlate() throws Exception {
        flushRedis();
        jdbc.update("delete from jobs");
        token = registerAndLogin("worker");
    }

    @AfterEach
    void stopWorkers() {
        // Open every gate first so a stopping worker does not wait for a handler that never returns.
        controlled.openAll();
        workers.forEach(Worker::stop);
        workers.clear();
    }

    /** concurrency jobs at a time, the given lease, and fast polling so tests stay quick. */
    protected static WorkerProperties props(int concurrency, long leaseMs) {
        return new WorkerProperties(true, concurrency, leaseMs, 20, 100);
    }

    protected Worker startWorker(WorkerProperties properties) {
        Worker worker = workerFactory.create(properties);
        worker.start();
        workers.add(worker);
        return worker;
    }

    protected UUID submitJob(String userToken, String type, int priority, String payloadJson, int timeoutSeconds)
            throws Exception {
        String body = """
                {"type": "%s", "priority": %d, "timeoutSeconds": %d, "payload": %s}
                """.formatted(type, priority, timeoutSeconds, payloadJson);
        return UUID.fromString(submitOk(userToken, body));
    }

    protected UUID submitJob(String type, int priority, String payloadJson) throws Exception {
        return submitJob(token, type, priority, payloadJson, 300);
    }

    /** Runs the promoter until nothing more is due: every submitted job becomes QUEUED and ready. */
    protected void promoteAll() {
        while (!promoter.promoteDue().isEmpty()) {
            // keep going
        }
    }

    protected String statusOf(UUID id) {
        return jdbc.queryForObject("select status from jobs where id = ?", String.class, id);
    }

    protected List<String> outcomesOf(UUID id) {
        return jdbc.queryForList("select outcome from job_attempts where job_id = ? order by id", String.class, id);
    }

    protected boolean inReady(UUID id) {
        return redis.opsForZSet().score(RedisKeys.READY, id.toString()) != null;
    }

    protected boolean inProcessing(UUID id) {
        return redis.opsForZSet().score(RedisKeys.PROCESSING, id.toString()) != null;
    }

    protected void awaitStatus(UUID id, String expected) {
        await().atMost(PATIENCE).untilAsserted(() -> assertThat(statusOf(id)).isEqualTo(expected));
    }

    protected void flushRedis() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}
