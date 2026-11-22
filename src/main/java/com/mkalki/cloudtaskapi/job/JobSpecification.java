package com.mkalki.cloudtaskapi.job;

import org.springframework.data.jpa.domain.Specification;

public class JobSpecification {

    public static Specification<Job> byOwnerId(Long ownerId) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Job> byStatus(JobStatus status) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Job> byType(String type) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("type"), type);
    }
}
