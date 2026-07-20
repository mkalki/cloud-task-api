package com.mkalki.cloudtaskapi.repository;

import com.mkalki.cloudtaskapi.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByCompleted(boolean completed, Pageable pageable);
}
