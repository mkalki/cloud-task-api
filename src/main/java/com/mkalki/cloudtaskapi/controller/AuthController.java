package com.mkalki.cloudtaskapi.controller;

import com.mkalki.cloudtaskapi.dto.LoginRequest;
import com.mkalki.cloudtaskapi.dto.RegisterRequest;
import com.mkalki.cloudtaskapi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public String login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void  register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }
}
