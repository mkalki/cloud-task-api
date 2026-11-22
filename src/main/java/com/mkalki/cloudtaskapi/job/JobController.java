package com.mkalki.cloudtaskapi.job;

import com.mkalki.cloudtaskapi.job.dto.CreateJobRequest;
import com.mkalki.cloudtaskapi.job.dto.JobResponse;
import com.mkalki.cloudtaskapi.job.dto.JobSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Jobs",
        description = "Submit, inspect and cancel background jobs. Jobs are private to their owner.")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @Operation(summary = "Submit a job.",
            description = "Creates a job in PENDING status. Send an Idempotency-Key header to make "
                    + "retries safe: a repeated key returns the original job with 200 instead of creating another.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = {
                            @ExampleObject(name = "Run as soon as possible",
                                    value = "{\"type\": \"demo.work\", \"payload\": {\"durationMs\": 500}}"),
                            @ExampleObject(name = "Delayed, high priority, 5 attempts",
                                    value = "{\"type\": \"demo.work\", \"payload\": {\"durationMs\": 500}, "
                                            + "\"priority\": 9, \"delaySeconds\": 60, \"maxAttempts\": 5, "
                                            + "\"timeoutSeconds\": 120}")
                    })))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Job created."),
            @ApiResponse(responseCode = "200", description = "Idempotency-Key matched an existing job; it is returned unchanged."),
            @ApiResponse(responseCode = "400", description = "Unknown type or invalid field."),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token."),
            @ApiResponse(responseCode = "413", description = "Payload is larger than 64 KB."),
            @ApiResponse(responseCode = "429", description = "Rate limit hit, or the active-job limit is reached.")
    })
    @PostMapping
    public ResponseEntity<JobResponse> submit(
            @Valid @RequestBody CreateJobRequest request,
            @Parameter(in = ParameterIn.HEADER,
                    description = "Optional. Unique per user. Reusing it returns the original job.")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        SubmitResult result = jobService.submit(request, idempotencyKey);

        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/v1/jobs/" + result.job().id()))
                    .body(result.job());
        }
        return ResponseEntity.ok(result.job());
    }

    @Operation(summary = "List your jobs.",
            description = "Paginated list of the caller's jobs, newest first by default. "
                    + "Optional filters: status and type. Rows do not include the payload; "
                    + "use GET /api/v1/jobs/{id} for it.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "A page of jobs."),
            @ApiResponse(responseCode = "400", description = "Invalid page, size, sort or filter.")
    })
    @GetMapping
    public Page<JobSummaryResponse> list(
            @Parameter(description = "Zero-based page index.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to 100.")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "field or field,asc or field,desc. "
                    + "Fields: createdAt, updatedAt, runAt, priority, status, type.")
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @Parameter(description = "Only jobs in this status.")
            @RequestParam(required = false) JobStatus status,
            @Parameter(description = "Only jobs of this exact type.")
            @RequestParam(required = false) String type) {
        return jobService.list(status, type, page, size, sort);
    }

    @Operation(summary = "Get a job.",
            description = "Returns the job and every attempt made so far. Returns 404 for jobs owned by other users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The job with its attempts."),
            @ApiResponse(responseCode = "404", description = "No such job for this user.")
    })
    @GetMapping("/{id}")
    public JobResponse get(@Parameter(description = "Job id (UUID).") @PathVariable UUID id) {
        return jobService.get(id);
    }

    @Operation(summary = "Cancel a job.",
            description = "Cancels a job that has not finished. Cancelling an already cancelled job succeeds "
                    + "without change. Jobs that already succeeded, died or failed upstream return 409.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The job is CANCELLED."),
            @ApiResponse(responseCode = "404", description = "No such job for this user."),
            @ApiResponse(responseCode = "409", description = "The job already finished and cannot be cancelled.")
    })
    @PostMapping("/{id}/cancel")
    public JobResponse cancel(@Parameter(description = "Job id (UUID).") @PathVariable UUID id) {
        return jobService.cancel(id);
    }
}
