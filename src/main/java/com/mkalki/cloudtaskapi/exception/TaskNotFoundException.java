package com.mkalki.cloudtaskapi.exception;

public class TaskNotFoundException extends RuntimeException
{
    public TaskNotFoundException(String message)
    {
        super(message);
    }
}
