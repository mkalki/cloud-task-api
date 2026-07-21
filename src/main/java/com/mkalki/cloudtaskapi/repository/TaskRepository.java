package com.mkalki.cloudtaskapi.repository;

import com.mkalki.cloudtaskapi.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, Long>,
        JpaSpecificationExecutor<Task> {

}
