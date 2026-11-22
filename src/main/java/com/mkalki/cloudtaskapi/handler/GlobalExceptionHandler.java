package com.mkalki.cloudtaskapi.handler;

import com.mkalki.cloudtaskapi.exception.*;
import com.mkalki.cloudtaskapi.job.exception.ActiveJobLimitExceededException;
import com.mkalki.cloudtaskapi.job.exception.IllegalJobTransitionException;
import com.mkalki.cloudtaskapi.job.exception.InvalidJobRequestException;
import com.mkalki.cloudtaskapi.job.exception.JobNotCancellableException;
import com.mkalki.cloudtaskapi.job.exception.JobNotFoundException;
import com.mkalki.cloudtaskapi.job.exception.PayloadTooLargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
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

    @ExceptionHandler(TaskAccessDeniedException.class)
    public ResponseEntity<String> handleTaskAccessDeniedException(
            TaskAccessDeniedException ex){
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshTokenException(
            InvalidRefreshTokenException ex){
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthenticationException(
            AuthenticationException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("Invalid username or password");
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<String> handleJobNotFoundException(
            JobNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidJobRequestException.class)
    public ResponseEntity<String> handleInvalidJobRequestException(
            InvalidJobRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<String> handlePayloadTooLargeException(
            PayloadTooLargeException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ex.getMessage());
    }

    @ExceptionHandler(ActiveJobLimitExceededException.class)
    public ResponseEntity<String> handleActiveJobLimitExceededException(
            ActiveJobLimitExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ex.getMessage());
    }

    @ExceptionHandler(JobNotCancellableException.class)
    public ResponseEntity<String> handleJobNotCancellableException(
            JobNotCancellableException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<String> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("The job was changed by another request. Retry the request.");
    }

    @ExceptionHandler(IllegalJobTransitionException.class)
    public ResponseEntity<String> handleIllegalJobTransitionException(
            IllegalJobTransitionException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("The job cannot move from " + ex.getFrom() + " to " + ex.getTo() + ".");
    }
}
