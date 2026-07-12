package com.mkalki.cloudtaskapi.model;

public class Greeting {

    private String message;
    private String author;

    public Greeting(String message,String author){
        this.message=message;
        this.author=author;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }
}
