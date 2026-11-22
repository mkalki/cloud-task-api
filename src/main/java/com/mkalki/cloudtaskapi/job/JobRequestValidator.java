package com.mkalki.cloudtaskapi.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkalki.cloudtaskapi.job.dto.CreateJobRequest;
import com.mkalki.cloudtaskapi.job.exception.InvalidJobRequestException;
import com.mkalki.cloudtaskapi.job.exception.PayloadTooLargeException;
import com.mkalki.cloudtaskapi.job.handler.JobHandlerRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Rules that Bean Validation cannot express (PRD FR-1.4). Field ranges such as
 * priority 0-9 are already checked by annotations on {@link CreateJobRequest}.
 */
@Component
public class JobRequestValidator {

    static final int DEFAULT_PRIORITY = 5;
    static final int DEFAULT_MAX_ATTEMPTS = 3;
    static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final JobHandlerRegistry registry;
    private final JobProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JobRequestValidator(
            JobHandlerRegistry registry,
            JobProperties properties,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public ValidatedJob validate(CreateJobRequest request) {
        String type = request.type() == null ? "" : request.type().trim();
        if (type.isEmpty()) {
            throw new InvalidJobRequestException("Job type is required");
        }
        if (!registry.isRegistered(type)) {
            // The client's value is not echoed back. The message lists the allowed types instead.
            throw new InvalidJobRequestException(
                    "Unknown job type. Valid types: " + registry.types().stream().sorted().toList());
        }

        Instant runAt = resolveRunAt(request, clock.instant());
        JsonNode payload = checkedPayload(request.payload());
        registry.find(type).ifPresent(handler -> handler.validatePayload(payload));

        return new ValidatedJob(
                type,
                payload,
                request.priority() != null ? request.priority() : DEFAULT_PRIORITY,
                runAt,
                request.maxAttempts() != null ? request.maxAttempts() : DEFAULT_MAX_ATTEMPTS,
                request.timeoutSeconds() != null ? request.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS
        );
    }

    /** A runAt in the past means "as soon as possible", so it becomes now. */
    private Instant resolveRunAt(CreateJobRequest request, Instant now) {
        if (request.runAt() != null && request.delaySeconds() != null) {
            throw new InvalidJobRequestException("Provide either runAt or delaySeconds, not both");
        }

        Instant latest = now.plus(properties.maxScheduleAheadDays(), ChronoUnit.DAYS);
        String tooFar = "Jobs can be scheduled at most "
                + properties.maxScheduleAheadDays() + " days ahead";

        if (request.delaySeconds() != null) {
            long maxDelay = Duration.between(now, latest).toSeconds();
            if (request.delaySeconds() < 0 || request.delaySeconds() > maxDelay) {
                throw new InvalidJobRequestException(tooFar);
            }
            return now.plusSeconds(request.delaySeconds());
        }

        if (request.runAt() != null) {
            if (request.runAt().isAfter(latest)) {
                throw new InvalidJobRequestException(tooFar);
            }
            return request.runAt().isBefore(now) ? now : request.runAt();
        }

        return now;
    }

    private JsonNode checkedPayload(JsonNode payload) {
        JsonNode value = (payload == null || payload.isNull())
                ? objectMapper.createObjectNode()
                : payload;

        int size;
        try {
            size = objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException e) {
            throw new InvalidJobRequestException("Payload is not valid JSON");
        }

        if (size > properties.maxPayloadBytes()) {
            throw new PayloadTooLargeException(
                    "Payload exceeds " + properties.maxPayloadBytes() + " bytes");
        }
        return value;
    }
}
