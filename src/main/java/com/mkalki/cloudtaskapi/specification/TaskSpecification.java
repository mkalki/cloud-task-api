package com.mkalki.cloudtaskapi.specification;

import com.mkalki.cloudtaskapi.model.Task;
import org.springframework.data.jpa.domain.Specification;

public class TaskSpecification {

    public static Specification<Task> byCompleted(boolean completed) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("completed"),
                        completed
                );
    }

    public static Specification<Task> titleContains(String title) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.like(
                        criteriaBuilder.lower(
                        root.get("title")),
                        "%" + title.toLowerCase() + "%"
                );
    }
}
