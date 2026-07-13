package com.mkalki.cloudtaskapi.service;

import com.mkalki.cloudtaskapi.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskService {

    private List<Task> tasks=new ArrayList<>();

    public TaskService() {
        tasks.add(new Task(
                1L,
                "Learn Spring Boot",
                "Complete Lesson 3",
                false));

        tasks.add(new Task(
                2L,
                "Learn collection frameworks",
                "Complete Lesson 1",
                false));

        tasks.add(new Task(
                3L,
                "Solve LeetCode",
                "2 Sum",
                false));
    }


    public List<Task> getAllTasks() {
        return tasks;
    }
}
