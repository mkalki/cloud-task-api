package com.mkalki.cloudtaskapi.job;

import com.jayway.jsonpath.JsonPath;
import com.mkalki.cloudtaskapi.queue.QueueService;
import com.mkalki.cloudtaskapi.queue.RedisKeys;
import com.mkalki.cloudtaskapi.scheduler.QueueReconciler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A Redis failure must never fail the HTTP request, and the reconciler must repair the gap. */
@SpringBootTest
@AutoConfigureMockMvc
class JobSubmitRedisFailureIntegrationTest extends JobApiTestSupport {

    @MockitoSpyBean
    private QueueService queueService;

    @Autowired
    private QueueReconciler reconciler;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void submitStillReturns201WhenRedisFailsAndTheReconcilerRepairsTheQueue() throws Exception {
        String token = registerAndLogin("redis-fail");
        doThrow(new RedisConnectionFailureException("Redis is down"))
                .when(queueService).enqueueScheduled(any(UUID.class), anyInt(), anyLong());

        String body = submit(token, """
                {"type": "demo.work", "delaySeconds": 3600}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(JsonPath.read(body, "$.id"));
        reset(queueService);

        assertThat(inScheduled(jobId)).as("the enqueue failed").isFalse();

        reconciler.reconcile();

        assertThat(inScheduled(jobId)).isTrue();
    }

    @Test
    void cancelStillReturns200WhenRedisFailsAndTheRowIsCancelled() throws Exception {
        String token = registerAndLogin("redis-fail-cancel");
        String id = submitOk(token, DEMO_JOB);
        doThrow(new RedisConnectionFailureException("Redis is down"))
                .when(queueService).remove(any(UUID.class));

        cancelJob(token, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        reset(queueService);

        assertThat(jdbc.queryForObject("select status from jobs where id = ?::uuid", String.class, id))
                .isEqualTo("CANCELLED");
    }

    private boolean inScheduled(UUID id) {
        return redis.opsForZSet().range(RedisKeys.SCHEDULED, 0, -1)
                .stream().anyMatch(member -> member.endsWith(":" + id));
    }
}
