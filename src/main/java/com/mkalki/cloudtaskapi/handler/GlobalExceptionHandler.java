package com.mkalki.cloudtaskapi.handler;

import com.mkalki.cloudtaskapi.exception.InvalidDateRangeException;
import com.mkalki.cloudtaskapi.exception.TaskNotFoundException;
import com.mkalki.cloudtaskapi.exception.UsernameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<String> handleInvalidDateRangeException(
            InvalidDateRangeException ex) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<String> handleTaskNotFoundException(
            TaskNotFoundException ex){

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex
    ){

            Map<String, String> errors = new HashMap<>();

            for (FieldError fieldError : ex.getBindingResult().getFieldErrors() ) {
                errors.put(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                );
            }
            return ResponseEntity.badRequest().body(errors);

    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<String> handleUsernameAlreadyExistsException(
            UsernameAlreadyExistsException ex
    ){
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ex.getMessage());
    }
}
