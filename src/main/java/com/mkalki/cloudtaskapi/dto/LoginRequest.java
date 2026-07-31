package com.mkalki.cloudtaskapi.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String username;
    @NotBlank
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
