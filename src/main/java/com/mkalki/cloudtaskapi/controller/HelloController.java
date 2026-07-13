package com.mkalki.cloudtaskapi.controller;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.model.Task;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
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
    public Task createTask(@RequestBody CreateTaskRequest request){
        return taskService.createTask(request);
    }
}
