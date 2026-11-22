package com.mkalki.cloudtaskapi.job.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Schema(description = "Request to submit a background job. Provide runAt or delaySeconds, not both.")
public record CreateJobRequest(

        @Schema(description = "Registered job type.", example = "demo.work")
        @NotBlank(message = "Job type is required")
        @Size(max = 100, message = "Job type cannot exceed 100 characters")
        String type,

        @Schema(description = "JSON payload for the handler, at most 64 KB.",
                example = "{\"durationMs\": 500}")
        JsonNode payload,

        @Schema(description = "0 (lowest) to 9 (highest). Defaults to 5.", example = "5")
        @Min(value = 0, message = "Priority must be between 0 and 9")
        @Max(value = 9, message = "Priority must be between 0 and 9")
        Integer priority,

        @Schema(description = "Earliest time to run, ISO-8601 UTC. At most 1 year ahead.",
                example = "2026-12-01T09:00:00Z")
        Instant runAt,

        @Schema(description = "Seconds to wait before the job becomes eligible to run.", example = "60")
        @Min(value = 0, message = "delaySeconds cannot be negative")
        Long delaySeconds,

        @Schema(description = "Total attempts allowed, 1 to 10. Defaults to 3.", example = "3")
        @Min(value = 1, message = "maxAttempts must be between 1 and 10")
        @Max(value = 10, message = "maxAttempts must be between 1 and 10")
        Integer maxAttempts,

        @Schema(description = "Per-attempt time limit in seconds, 1 to 3600. Defaults to 300.", example = "300")
        @Min(value = 1, message = "timeoutSeconds must be between 1 and 3600")
        @Max(value = 3600, message = "timeoutSeconds must be between 1 and 3600")
        Integer timeoutSeconds
) {
}
