package com.mkalki.cloudtaskapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateTaskRequest {

    @NotBlank(message = "Title cannot be blank")
    @Size(max=100, message = "Title cannot exceed 100 chracters")
    private String title;

    @Size(max=500, message = "Description cannot exceed 500 characters")
    private String description;
    private boolean completed;

    public UpdateTaskRequest() {

    }

    public UpdateTaskRequest(String title,
                             String description,
                             boolean completed) {
        this.title = title;
        this.description = description;
        this.completed = completed;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompleted() {
        return completed;
    }
}
