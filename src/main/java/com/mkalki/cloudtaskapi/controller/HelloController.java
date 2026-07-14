package com.mkalki.cloudtaskapi.controller;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.model.Task;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.mkalki.cloudtaskapi.model.Greeting;
import java.util.ArrayList;
import java.util.List;
import com.mkalki.cloudtaskapi.service.TaskService;

@RestController
public class HelloController {

    private final TaskService taskService;

    public HelloController(TaskService taskService){
        this.taskService = taskService;
    }

    @GetMapping("/hello")
    public Greeting hello(){
        Greeting greeting=new Greeting("hello world","mkalki");
        return greeting;
    }

    @GetMapping("/tasks")
    public List<Task> getTasks() {
        return taskService.getAllTasks();
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Task createTask(@RequestBody CreateTaskRequest request){
        return taskService.createTask(request);
    }
}
