package com.mkalki.cloudtaskapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateTaskRequest {

    @NotBlank(message="Title cannot be blank")
    @Size(max=100, message = "Title cannot exceed 100 characters")
    private String title;

    @Size(max=500, message = "Description cannot exceed 500 characters")
    private String description;

    public CreateTaskRequest(){

    }

    public CreateTaskRequest(String title,String description){
        this.title=title;
        this.description=description;
    }

    public String getTitle(){
        return title;
    }

    public String getDescription(){
        return description;
    }
}
