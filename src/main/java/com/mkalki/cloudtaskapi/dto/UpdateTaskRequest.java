package com.mkalki.cloudtaskapi.dto;

import com.mkalki.cloudtaskapi.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateTaskRequest {

    @NotBlank(message = "Title cannot be blank")
    @Size(max=100, message = "Title cannot exceed 100 characters")
    private String title;

    @Size(max=500, message = "Description cannot exceed 500 characters")
    private String description;
    private boolean completed;
    private Priority priority;

    public UpdateTaskRequest() {

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

    public Priority getPriority() {
        return priority;
    }
}
