package com.mkalki.cloudtaskapi.service;

import com.mkalki.cloudtaskapi.dto.AuthResponse;
import com.mkalki.cloudtaskapi.dto.LoginRequest;
import com.mkalki.cloudtaskapi.dto.RefreshTokenRequest;
import com.mkalki.cloudtaskapi.dto.RegisterRequest;
import com.mkalki.cloudtaskapi.entity.RefreshToken;
import com.mkalki.cloudtaskapi.entity.User;
import com.mkalki.cloudtaskapi.enums.Role;
import com.mkalki.cloudtaskapi.exception.UsernameAlreadyExistsException;
import com.mkalki.cloudtaskapi.repository.UserRepository;
import com.mkalki.cloudtaskapi.security.JwtService;
import com.mkalki.cloudtaskapi.security.RefreshTokenService;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse login(LoginRequest request){
       Authentication authentication=
               authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        User user = (User) authentication.getPrincipal();

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken);
    }

    public void register(RegisterRequest request){
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException("Username already exists");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request){
        RefreshToken refreshToken=
                refreshTokenService.getRefreshToken(request.getRefreshToken());

        refreshTokenService.validateRefreshToken(refreshToken);

        User user = refreshToken.getUser();

        refreshTokenService.revokeRefreshToken(refreshToken);

        String newRefreshToken = refreshTokenService.createRefreshToken(user);

        String accessToken = jwtService.generateToken(user);

        return new AuthResponse(accessToken, newRefreshToken);
    }

    public void logout(RefreshTokenRequest request){
        RefreshToken refreshToken=
                refreshTokenService.getRefreshToken(request.getRefreshToken());

        refreshTokenService.revokeRefreshToken(refreshToken);
    }
}
