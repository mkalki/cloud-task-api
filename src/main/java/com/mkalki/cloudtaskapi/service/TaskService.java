package com.mkalki.cloudtaskapi.service;

import com.mkalki.cloudtaskapi.dto.CreateTaskRequest;
import com.mkalki.cloudtaskapi.dto.UpdateTaskRequest;
import com.mkalki.cloudtaskapi.exception.TaskNotFoundException;
import com.mkalki.cloudtaskapi.model.Task;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
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

    public Task createTask(CreateTaskRequest request){

        Long nextId;

        if(tasks.isEmpty()){
            nextId=1L;
        }else{
            nextId=tasks.get(tasks.size()-1).getId()+1;
        }

        Task task=new Task(
                nextId,
                request.getTitle(),
                request.getDescription(),
                false
        );
        tasks.add(task);
        return task;
    }

    public Task updateTask(Long id,UpdateTaskRequest request){
        for(Task task:tasks){
            if(task.getId().equals(id)){
                task.setTitle(request.getTitle());
                task.setDescription(request.getDescription());
                task.setCompleted(request.isCompleted());

                return task;
            }

        }

        throw new TaskNotFoundException("Task not found");
    }
}
