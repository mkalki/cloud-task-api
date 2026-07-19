package com.mkalki.cloudtaskapi.controller;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.dto.UpdateTaskRequest;
import com.mkalki.cloudtaskapi.model.Task;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;

import com.mkalki.cloudtaskapi.service.TaskService;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService){
        this.taskService = taskService;
    }


    @GetMapping("/tasks/{id}")
    public Task getTaskById(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @GetMapping("/tasks")
    public Page<Task> getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String sort
    ) {

        String[] sortParts = sort.split(",");

        Sort sortObject;

        if(sortParts.length == 1){
            sortObject= Sort.by(sortParts[0]).ascending();
        }
        else if("desc".equalsIgnoreCase(sortParts[1])){
            sortObject=Sort.by(sortParts[0]).descending();
        }else {
            sortObject=Sort.by(sortParts[0]).ascending();
        }
        Pageable pageable = PageRequest.of(page, size ,sortObject);
        return taskService.getAllTasks(pageable);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Task createTask(@Valid @RequestBody CreateTaskRequest request){
        return taskService.createTask(request);
    }

    @PutMapping("/tasks/{id}")
    public Task updateTask( @PathVariable Long id,
                            @Valid @RequestBody UpdateTaskRequest request){
        return taskService.updateTask(id,request);
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id){
        taskService.deleteTask(id);
    }
}
