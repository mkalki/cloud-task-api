package com.mkalki.cloudtaskapi.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;

    public RegisterRequest() {

    }
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }


}
