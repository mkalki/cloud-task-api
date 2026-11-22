package com.mkalki.cloudtaskapi.job.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkalki.cloudtaskapi.job.exception.InvalidJobRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DemoWorkHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DemoWorkHandler handler = new DemoWorkHandler(mock(StringRedisTemplate.class));

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"durationMs\": 0}",
            "{\"durationMs\": 60000}",
            "{\"failProbability\": 0}",
            "{\"failProbability\": 1}",
            "{\"failProbability\": 0.25}",
            "{\"counterKey\": \"job-1.a_b\"}",
            "{\"durationMs\": 10, \"failProbability\": 0.5, \"counterKey\": \"k\", \"other\": \"ignored\"}",
            "{\"durationMs\": null}"
    })
    void validPayloadsAreAccepted(String json) throws Exception {
        assertThatCode(() -> handler.validatePayload(objectMapper.readTree(json))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "\"text\"",
            "5",
            "{\"durationMs\": -1}",
            "{\"durationMs\": 60001}",
            "{\"durationMs\": 1.5}",
            "{\"durationMs\": \"10\"}",
            "{\"durationMs\": 99999999999}",
            "{\"failProbability\": -0.1}",
            "{\"failProbability\": 1.1}",
            "{\"failProbability\": \"0.5\"}",
            "{\"counterKey\": \"\"}",
            "{\"counterKey\": 7}",
            "{\"counterKey\": \"has space\"}",
            "{\"counterKey\": \"slash/not/allowed\"}"
    })
    void invalidPayloadsAreRejectedWithAClearMessage(String json) throws Exception {
        JsonNode payload = objectMapper.readTree(json);

        assertThatThrownBy(() -> handler.validatePayload(payload))
                .isInstanceOf(InvalidJobRequestException.class)
                .hasMessageContaining("demo.work");
    }

    @Test
    void aRunWithFailProbabilityOneThrows() throws Exception {
        JobContext ctx = context("{\"failProbability\": 1}");

        assertThatThrownBy(() -> handler.handle(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed on purpose");
    }

    @Test
    void aRunWithFailProbabilityZeroSucceeds() throws Exception {
        JobContext ctx = context("{\"durationMs\": 5, \"failProbability\": 0}");

        assertThatCode(() -> handler.handle(ctx)).doesNotThrowAnyException();
    }

    @Test
    void theContextTextFormLeavesOutThePayload() throws Exception {
        JobContext ctx = context("{\"secret\": \"hunter2\"}");

        assertThat(ctx.toString()).doesNotContain("hunter2").contains("demo.work");
    }

    private JobContext context(String payloadJson) throws Exception {
        return new JobContext(
                UUID.randomUUID(), 1L, "demo.work", 1, objectMapper.readTree(payloadJson), () -> false);
    }
}
