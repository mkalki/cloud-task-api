package com.mkalki.cloudtaskapi.service;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.dto.TaskResponse;
import com.mkalki.cloudtaskapi.dto.UpdateTaskRequest;
import com.mkalki.cloudtaskapi.entity.Task;
import com.mkalki.cloudtaskapi.entity.User;
import com.mkalki.cloudtaskapi.enums.Priority;
import com.mkalki.cloudtaskapi.enums.Role;
import com.mkalki.cloudtaskapi.enums.Status;
import com.mkalki.cloudtaskapi.exception.InvalidDateRangeException;
import com.mkalki.cloudtaskapi.exception.TaskAccessDeniedException;
import com.mkalki.cloudtaskapi.exception.TaskNotFoundException;
import com.mkalki.cloudtaskapi.mapper.TaskMapper;
import com.mkalki.cloudtaskapi.repository.TaskRepository;
import com.mkalki.cloudtaskapi.repository.UserRepository;
import com.mkalki.cloudtaskapi.specification.TaskSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;

    public TaskService(TaskRepository taskRepository,
                       UserRepository userRepository,
                       TaskMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.taskMapper = taskMapper;
    }



    public Page<TaskResponse> getTasks(
            Status status,
            String title,
            LocalDate dueDate,
            LocalDate dueBefore,
            LocalDate dueAfter,
            Priority priority,
            String search,
            Pageable pageable) {
        if(dueAfter!=null && dueBefore!=null && dueAfter.isAfter(dueBefore)) {
            throw new InvalidDateRangeException
                    ("Invalid date range: dueAfter must be before dueBefore.");
        }
        User currentUser = getCurrentUser();

        Specification<Task> spec = TaskSpecification.notDeleted();
        if(currentUser.getRole()!=Role.ADMIN) {
            spec=spec.and(TaskSpecification.byOwnerId(currentUser.getId()));
        }

       if(status != null ){
           spec = spec.and(TaskSpecification.byStatus(status));
       }
       if(title != null && !title.isBlank()){
           spec =spec.and(TaskSpecification.titleContains(title));
       }
       if(dueDate != null){
           spec = spec.and(TaskSpecification.byDueDate(dueDate));
       }
       if(dueBefore != null){
           spec = spec.and(TaskSpecification.byDueBefore(dueBefore));
       }
       if(dueAfter != null){
           spec = spec.and(TaskSpecification.byDueAfter(dueAfter));
       }
       if(priority != null){
           spec = spec.and(TaskSpecification.byPriority(priority));
       }
        if(search != null && !search.isBlank()){
            spec = spec.and(TaskSpecification.bySearch(search));
        }
       return taskRepository.findAll(spec, pageable)
               .map(taskMapper::toResponse);

    }

    public TaskResponse createTask(CreateTaskRequest request){
        User currentUser = getCurrentUser();
        Priority priority = request.getPriority();
        if(priority == null){
            priority = Priority.LOW;
        }


        Task task = new Task(
                null,
                request.getTitle(),
                request.getDescription(),
                priority,
                request.getDueDate(),
                currentUser
        );

        return taskMapper.toResponse(taskRepository.save(task));
    }

    public Task getTaskById(Long id) {
        return taskRepository.findByIdAndDeletedFalse(id).orElseThrow(() ->
                new TaskNotFoundException("Task not found"));
    }

    public TaskResponse getTaskResponseById(Long id) {
        Task task = getTaskById(id);
        verifyTaskAccess(task);
        return taskMapper.toResponse(task);
    }

    public TaskResponse updateTask(Long id,UpdateTaskRequest request){

        Task task = getTaskById(id);
        verifyTaskAccess(task);
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

       return taskMapper.toResponse(taskRepository.save(task));

    }

    public void deleteTask(Long id) {

        Task task = getTaskById(id);
        verifyTaskAccess(task);
        task.setDeleted(true);
        task.setDeletedAt(LocalDateTime.now());

        taskRepository.save(task);
    }
    private void verifyTaskAccess(Task task){
        User  currentUser = getCurrentUser();
        if(task.getOwner().getId().equals(currentUser.getId())){
            return;
        }
        if(currentUser.getRole()== Role.ADMIN){
            return;
        }
        throw new TaskAccessDeniedException(
                "You are not allowed to perform this action.");
    }

    private User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found:" + username));
    }
}
