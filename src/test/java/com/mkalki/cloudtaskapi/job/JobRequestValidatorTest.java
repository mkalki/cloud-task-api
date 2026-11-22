package com.mkalki.cloudtaskapi.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkalki.cloudtaskapi.job.dto.CreateJobRequest;
import com.mkalki.cloudtaskapi.job.exception.InvalidJobRequestException;
import com.mkalki.cloudtaskapi.job.exception.PayloadTooLargeException;
import com.mkalki.cloudtaskapi.job.handler.DemoWorkHandler;
import com.mkalki.cloudtaskapi.job.handler.JobContext;
import com.mkalki.cloudtaskapi.job.handler.JobHandler;
import com.mkalki.cloudtaskapi.job.handler.JobHandlerRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobRequestValidatorTest {

    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");
    private static final int MAX_PAYLOAD_BYTES = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JobRequestValidator validator = new JobRequestValidator(
            new JobHandlerRegistry(List.of(new DemoWorkHandler())),
            new JobProperties(1000, MAX_PAYLOAD_BYTES, 365),
            Clock.fixed(NOW, ZoneOffset.UTC),
            objectMapper
    );

    @Test
    void minimalRequestGetsDefaults() {
        ValidatedJob job = validator.validate(request("demo.work", null, null, null, null, null, null));

        assertThat(job.type()).isEqualTo("demo.work");
        assertThat(job.priority()).isEqualTo(5);
        assertThat(job.maxAttempts()).isEqualTo(3);
        assertThat(job.timeoutSeconds()).isEqualTo(300);
        assertThat(job.runAt()).isEqualTo(NOW);
        assertThat(job.payload().isObject()).isTrue();
        assertThat(job.payload().isEmpty()).isTrue();
    }

    @Test
    void explicitValuesAreKept() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"a\":1}");

        ValidatedJob job = validator.validate(request("demo.work", payload, 9, null, 60L, 7, 120));

        assertThat(job.payload()).isEqualTo(payload);
        assertThat(job.priority()).isEqualTo(9);
        assertThat(job.maxAttempts()).isEqualTo(7);
        assertThat(job.timeoutSeconds()).isEqualTo(120);
        assertThat(job.runAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void typeIsTrimmed() {
        assertThat(validator.validate(request("  demo.work ", null, null, null, null, null, null)).type())
                .isEqualTo("demo.work");
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> validator.validate(request("rm.everything", null, null, null, null, null, null)))
                .isInstanceOf(InvalidJobRequestException.class)
                .hasMessage("Unknown job type: rm.everything");
    }

    @Test
    void blankTypeIsRejected() {
        assertThatThrownBy(() -> validator.validate(request("   ", null, null, null, null, null, null)))
                .isInstanceOf(InvalidJobRequestException.class);
    }

    @Test
    void runAtAndDelayTogetherAreRejected() {
        assertThatThrownBy(() -> validator.validate(
                request("demo.work", null, null, NOW.plusSeconds(10), 10L, null, null)))
                .isInstanceOf(InvalidJobRequestException.class)
                .hasMessageContaining("either runAt or delaySeconds");
    }

    @Test
    void runAtInThePastBecomesNow() {
        ValidatedJob job = validator.validate(
                request("demo.work", null, null, NOW.minusSeconds(3600), null, null, null));

        assertThat(job.runAt()).isEqualTo(NOW);
    }

    @Test
    void runAtExactlyOneYearAheadIsAccepted() {
        Instant latest = Instant.parse("2027-12-01T10:00:00Z");

        assertThat(validator.validate(request("demo.work", null, null, latest, null, null, null)).runAt())
                .isEqualTo(latest);
    }

    @Test
    void runAtMoreThanOneYearAheadIsRejected() {
        Instant tooFar = Instant.parse("2027-12-01T10:00:01Z");

        assertThatThrownBy(() -> validator.validate(request("demo.work", null, null, tooFar, null, null, null)))
                .isInstanceOf(InvalidJobRequestException.class)
                .hasMessageContaining("365 days");
    }

    @Test
    void delayBeyondOneYearIsRejected() {
        long twoYears = 2L * 365 * 24 * 3600;

        assertThatThrownBy(() -> validator.validate(request("demo.work", null, null, null, twoYears, null, null)))
                .isInstanceOf(InvalidJobRequestException.class);
    }

    @Test
    void negativeDelayIsRejected() {
        assertThatThrownBy(() -> validator.validate(request("demo.work", null, null, null, -1L, null, null)))
                .isInstanceOf(InvalidJobRequestException.class);
    }

    @Test
    void payloadAtTheLimitIsAccepted() throws Exception {
        // {"k":"<92 x>"} is exactly 100 bytes.
        JsonNode payload = objectMapper.readTree("{\"k\":\"" + "x".repeat(92) + "\"}");
        assertThat(objectMapper.writeValueAsBytes(payload)).hasSize(MAX_PAYLOAD_BYTES);

        assertThat(validator.validate(request("demo.work", payload, null, null, null, null, null)).payload())
                .isEqualTo(payload);
    }

    @Test
    void payloadOverTheLimitIsRejected() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"k\":\"" + "x".repeat(93) + "\"}");

        assertThatThrownBy(() -> validator.validate(request("demo.work", payload, null, null, null, null, null)))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void registryRejectsTwoHandlersForOneType() {
        JobHandler duplicate = new JobHandler() {
            @Override
            public String type() {
                return "demo.work";
            }

            @Override
            public void handle(JobContext ctx) {
            }
        };

        assertThatThrownBy(() -> new JobHandlerRegistry(List.of(new DemoWorkHandler(), duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo.work");
    }

    private static CreateJobRequest request(
            String type, JsonNode payload, Integer priority, Instant runAt,
            Long delaySeconds, Integer maxAttempts, Integer timeoutSeconds) {
        return new CreateJobRequest(type, payload, priority, runAt, delaySeconds, maxAttempts, timeoutSeconds);
    }
}
