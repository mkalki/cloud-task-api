package com.mkalki.cloudtaskapi.dto;

import com.mkalki.cloudtaskapi.enums.Priority;
import com.mkalki.cloudtaskapi.enums.Status;
import jakarta.validation.constraints.Size;

public class UpdateTaskRequest {


    @Size(max=100, message = "Title cannot exceed 100 characters")
    private String title;

    @Size(max=500, message = "Description cannot exceed 500 characters")
    private String description;
    private Status status ;
    private Priority priority;

    public UpdateTaskRequest() {

    }


    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }
}
