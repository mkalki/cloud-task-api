package com.mkalki.cloudtaskapi.dto;

public class CreateTaskRequest {

    private String title;
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
