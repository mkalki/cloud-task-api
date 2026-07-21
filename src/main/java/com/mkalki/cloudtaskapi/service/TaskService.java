package com.mkalki.cloudtaskapi.service;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.dto.UpdateTaskRequest;
import com.mkalki.cloudtaskapi.exception.TaskNotFoundException;
import com.mkalki.cloudtaskapi.model.Task;
import com.mkalki.cloudtaskapi.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;



@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;

    }



    public Page<Task> getAllTasks(
            Boolean completed,
            String title,
            Pageable pageable) {
        boolean hasTitle = title != null && !title.isBlank();

       if(completed == null && !hasTitle){
           return taskRepository.findAll(pageable);
       }
       if(completed != null && !hasTitle){
           return taskRepository.findByCompleted(completed, pageable);
       }
       if(completed == null ){
           return taskRepository.findByTitleContainingIgnoreCase(title, pageable);
       }
       return taskRepository.findByCompletedAndTitleContainingIgnoreCase(
               completed,
               title,
               pageable
       );
    }

    public Task createTask(CreateTaskRequest request){

        Task task = new Task(
                null,
                request.getTitle(),
                request.getDescription(),
                false
        );

        return taskRepository.save(task);
    }

    public Task getTaskById(Long id) {
        return taskRepository.findById(id).orElseThrow(() ->
                new TaskNotFoundException("Task not found"));
    }

    public Task updateTask(Long id,UpdateTaskRequest request){

        Task task = getTaskById(id);
       task.setTitle(request.getTitle());
       task.setDescription(request.getDescription());
       task.setCompleted(request.isCompleted());

       return taskRepository.save(task);

    }

    public void deleteTask(Long id) {

        Task task = getTaskById(id);
        taskRepository.delete(task);
    }


}
