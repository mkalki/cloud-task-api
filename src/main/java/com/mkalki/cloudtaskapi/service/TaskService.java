package com.mkalki.cloudtaskapi.service;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.dto.UpdateTaskRequest;
import com.mkalki.cloudtaskapi.enums.Priority;
import com.mkalki.cloudtaskapi.enums.Status;
import com.mkalki.cloudtaskapi.exception.TaskNotFoundException;
import com.mkalki.cloudtaskapi.entity.Task;
import com.mkalki.cloudtaskapi.repository.TaskRepository;
import com.mkalki.cloudtaskapi.specification.TaskSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;


@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;

    }



    public Page<Task> getAllTasks(
            Status status,
            String title,
            Pageable pageable) {
        Specification<Task> spec = TaskSpecification.notDeleted();

       if(status != null ){
           spec = spec.and(TaskSpecification.byStatus(status));
       }
       if(title != null && !title.isBlank()){
           spec =spec.and(TaskSpecification.titleContains(title));
       }

       return taskRepository.findAll(spec, pageable);

    }

    public Task createTask(CreateTaskRequest request){
        Priority priority = request.getPriority();
        if(priority == null){
            priority = Priority.LOW;
        }

        Task task = new Task(
                null,
                request.getTitle(),
                request.getDescription(),
                Status.TODO,
                priority,
                request.getDueDate()
        );

        return taskRepository.save(task);
    }

    public Task getTaskById(Long id) {
        return taskRepository.findByIdAndDeletedFalse(id).orElseThrow(() ->
                new TaskNotFoundException("Task not found"));
    }

    public Task updateTask(Long id,UpdateTaskRequest request){

        Task task = getTaskById(id);
        if(request.getTitle() != null){
            task.setTitle(request.getTitle());
        }
       if(request.getDescription() != null){
           task.setDescription(request.getDescription());
       }
       if(request.getStatus() != null){
           task.setStatus(request.getStatus());
       }
       if(request.getPriority() != null){
           task.setPriority(request.getPriority());
       }
       if(request.getDueDate() != null){
           task.setDueDate(request.getDueDate());
       }

       return taskRepository.save(task);

    }

    public void deleteTask(Long id) {

        Task task = getTaskById(id);
        task.setDeleted(true);
        task.setDeletedAt(LocalDateTime.now());

        taskRepository.save(task);
    }


}
