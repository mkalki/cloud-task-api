package com.mkalki.cloudtaskapi.controller;

import com.mkalki.cloudtaskapi.model.Task;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import com.mkalki.cloudtaskapi.model.Greeting;
import java.util.ArrayList;
import java.util.List;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Greeting hello(){
        Greeting greeting=new Greeting("hello world","mkalki");
        return greeting;
    }

    @GetMapping("/tasks")
    public List<Task> getTasks() {
        List<Task> tasks = new ArrayList<>();
        Task task1 = new Task(1L, "Learn Spring Boot",
                "Complete Lesson 3", false);

        Task task2 = new Task(2L, "Learn collection frameworks",
                "Complete Lesson 1", false);

        Task task3 = new Task(3L, "solve leetcode",
                "2 sum", false);

        tasks.add(task1);
        tasks.add(task2);
        tasks.add(task3);

        return tasks;

    }
}
