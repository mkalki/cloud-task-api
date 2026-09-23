package com.mkalki.cloudtaskapi.dto;

import com.mkalki.cloudtaskapi.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class CreateTaskRequest {

    @NotBlank(message="Title cannot be blank")
    @Size(max=100, message = "Title cannot exceed 100 characters")
    private String title;

    @Size(max=500, message = "Description cannot exceed 500 characters")
    private String description;
    private LocalDateTime dueAt;
    private Priority priority;

    public CreateTaskRequest(){

    }

    public String getTitle(){
        return title;
    }

    public String getDescription(){
        return description;
    }

    public Priority getPriority(){
        return priority;
    }

    public LocalDateTime getDueAt(){
        return dueAt;
    }

    public CreateTaskRequest(String title,
                             String description,
                             LocalDateTime dueAt,
                             Priority priority){
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.priority = priority;
    }


}
