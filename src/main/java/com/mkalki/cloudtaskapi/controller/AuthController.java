package com.mkalki.cloudtaskapi.controller;

import com.mkalki.cloudtaskapi.dto.AuthResponse;
import com.mkalki.cloudtaskapi.dto.LoginRequest;
import com.mkalki.cloudtaskapi.dto.RefreshTokenRequest;
import com.mkalki.cloudtaskapi.dto.RegisterRequest;
import com.mkalki.cloudtaskapi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void  register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {

        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
