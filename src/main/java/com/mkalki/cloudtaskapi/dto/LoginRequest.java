package com.mkalki.cloudtaskapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {
    
    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username must not exceed 50 characters")
    private String username;
    
    @NotBlank(message = "Password is required")
    @Size(max = 100, message = "Password must not exceed 100 characters")
    private String password;

    public LoginRequest(){

    }

    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
}
