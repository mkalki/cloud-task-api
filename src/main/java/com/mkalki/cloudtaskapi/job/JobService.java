package com.mkalki.cloudtaskapi.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mkalki.cloudtaskapi.context.RequestContextProvider;
import com.mkalki.cloudtaskapi.enums.AuditAction;
import com.mkalki.cloudtaskapi.enums.AuditResourceType;
import com.mkalki.cloudtaskapi.job.dto.CreateJobRequest;
import com.mkalki.cloudtaskapi.job.dto.JobResponse;
import com.mkalki.cloudtaskapi.job.exception.ActiveJobLimitExceededException;
import com.mkalki.cloudtaskapi.job.exception.InvalidJobRequestException;
import com.mkalki.cloudtaskapi.job.exception.JobNotCancellableException;
import com.mkalki.cloudtaskapi.job.exception.JobNotFoundException;
import com.mkalki.cloudtaskapi.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "updatedAt", "runAt", "priority", "status", "type");

    private final JobRepository jobRepository;
    private final JobAttemptRepository attemptRepository;
    private final JobRequestValidator validator;
    private final JobStateMachine stateMachine;
    private final JobProperties properties;
    private final JobMapper mapper;
    private final AuditService auditService;
    private final RequestContextProvider requestContextProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public JobService(
            JobRepository jobRepository,
            JobAttemptRepository attemptRepository,
            JobRequestValidator validator,
            JobStateMachine stateMachine,
            JobProperties properties,
            JobMapper mapper,
            AuditService auditService,
            RequestContextProvider requestContextProvider,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.validator = validator;
        this.stateMachine = stateMachine;
        this.properties = properties;
        this.mapper = mapper;
        this.auditService = auditService;
        this.requestContextProvider = requestContextProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Not annotated with @Transactional on purpose. When two requests race on the same
     * Idempotency-Key, the loser's insert fails the unique index and PostgreSQL aborts
     * that transaction. The fallback read must therefore run after it ends.
     */
    public SubmitResult submit(CreateJobRequest request, String idempotencyKey) {
        Long userId = currentUserId();
        String key = normalizeKey(idempotencyKey);
        ValidatedJob validated = validator.validate(request);

        if (key != null) {
            Optional<Job> existing = jobRepository.findByOwnerIdAndIdempotencyKey(userId, key);
            if (existing.isPresent()) {
                return new SubmitResult(mapper.toResponse(existing.get()), false);
            }
        }

        try {
            return transactionTemplate.execute(status -> create(userId, key, validated));
        } catch (DataIntegrityViolationException e) {
            if (key == null) {
                throw e;
            }
            Job winner = jobRepository.findByOwnerIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() -> e);
            return new SubmitResult(mapper.toResponse(winner), false);
        }
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID id) {
        Job job = findOwnedJob(id);
        List<JobAttempt> attempts =
                attemptRepository.findByJobIdOrderByAttemptNoAscIdAsc(job.getId());
        return mapper.toResponse(job, attempts);
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> list(JobStatus status, String type, int page, int size, String sort) {
        Specification<Job> spec = JobSpecification.byOwnerId(currentUserId());
        if (status != null) {
            spec = spec.and(JobSpecification.byStatus(status));
        }
        if (type != null && !type.isBlank()) {
            spec = spec.and(JobSpecification.byType(type.trim()));
        }
        return jobRepository.findAll(spec, toPageable(page, size, sort))
                .map(mapper::toResponse);
    }

    /**
     * PostgreSQL is authoritative: this only changes the database row. Removing the job
     * from Redis queues is added in F2. Cancelling an already cancelled job is a no-op.
     */
    @Transactional
    public JobResponse cancel(UUID id) {
        Job job = findOwnedJob(id);

        if (job.getStatus() == JobStatus.CANCELLED) {
            return mapper.toResponse(job);
        }
        if (job.getStatus().isTerminal()) {
            throw new JobNotCancellableException(
                    "Job is already " + job.getStatus() + " and cannot be cancelled");
        }

        JobStatus previous = job.getStatus();
        stateMachine.transition(job, JobStatus.CANCELLED, clock.instant());
        Job saved = jobRepository.save(job);

        ObjectNode changes = objectMapper.createObjectNode();
        changes.put("jobId", saved.getId().toString());
        changes.put("type", saved.getType());
        changes.put("previousStatus", previous.name());
        auditService.log(AuditAction.JOB_CANCELLED, AuditResourceType.JOB, null, changes);

        log.info("job cancelled jobId={} type={} previousStatus={}",
                saved.getId(), saved.getType(), previous);
        return mapper.toResponse(saved);
    }

    private SubmitResult create(Long userId, String key, ValidatedJob validated) {
        long active = jobRepository.countByOwnerIdAndStatusIn(userId, JobStatus.activeStatuses());
        if (active >= properties.maxActivePerUser()) {
            throw new ActiveJobLimitExceededException(
                    "Active job limit of " + properties.maxActivePerUser() + " reached");
        }

        Instant now = clock.instant();
        Job job = Job.newPending(UUID.randomUUID(), userId, validated, key, now);
        // Flush now so a duplicate Idempotency-Key fails inside this transaction callback.
        Job saved = jobRepository.saveAndFlush(job);

        // audit_logs.resource_id is a BIGINT and job ids are UUIDs, so the id goes in "changes".
        // The payload is never audited or logged: it may contain personal data.
        ObjectNode changes = objectMapper.createObjectNode();
        changes.put("jobId", saved.getId().toString());
        changes.put("type", saved.getType());
        auditService.log(AuditAction.JOB_SUBMITTED, AuditResourceType.JOB, null, changes);

        log.info("job submitted jobId={} type={} attempt={}",
                saved.getId(), saved.getType(), saved.getAttempt());
        return new SubmitResult(mapper.toResponse(saved), true);
    }

    /** A job that is missing and a job owned by someone else both look like 404. */
    private Job findOwnedJob(UUID id) {
        return jobRepository.findByIdAndOwnerId(id, currentUserId())
                .orElseThrow(() -> new JobNotFoundException("Job not found"));
    }

    private Long currentUserId() {
        return requestContextProvider.getCurrentContext().userId();
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String key = idempotencyKey.trim();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidJobRequestException(
                    "Idempotency-Key cannot exceed " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return key;
    }

    private Pageable toPageable(int page, int size, String sort) {
        if (page < 0) {
            throw new InvalidJobRequestException("page cannot be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidJobRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new InvalidJobRequestException(
                    "Cannot sort by " + field + ". Allowed: " + SORTABLE_FIELDS);
        }
        boolean descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        Sort sortObject = (descending ? Sort.by(field).descending() : Sort.by(field).ascending())
                .and(Sort.by("id"));
        return PageRequest.of(page, size, sortObject);
    }
}
