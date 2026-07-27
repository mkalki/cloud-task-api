package com.mkalki.cloudtaskapi.specification;

import com.mkalki.cloudtaskapi.entity.Task;
import com.mkalki.cloudtaskapi.enums.Priority;
import com.mkalki.cloudtaskapi.enums.Status;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class TaskSpecification {

    public static Specification<Task> byDueDate(LocalDate dueDate) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("dueDate"),
                        dueDate
                );
    }

    public static Specification<Task> byDueBefore(LocalDate dueBefore) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.lessThan(
                        root.get("dueDate"),
                        dueBefore
                );
    }

    public static Specification<Task> byDueAfter(LocalDate dueAfter) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.greaterThan(
                        root.get("dueDate"),
                        dueAfter
                );
    }

    public static Specification<Task> byStatus(Status status) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("status"),
                        status
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

    public static Specification<Task> notDeleted(){
        return (root, criteriaQuery, criteriaBuilder) ->
            criteriaBuilder.isFalse(root.get("deleted"));
    }

    public static Specification<Task> byPriority(Priority priority) {
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("priority"), priority);
    }
}
