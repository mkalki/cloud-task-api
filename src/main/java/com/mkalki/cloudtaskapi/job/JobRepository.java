package com.mkalki.cloudtaskapi.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    Optional<Job> findByIdAndOwnerId(UUID id, Long ownerId);

    Optional<Job> findByOwnerIdAndIdempotencyKey(Long ownerId, String idempotencyKey);

    long countByOwnerIdAndStatusIn(Long ownerId, Collection<JobStatus> statuses);
}
