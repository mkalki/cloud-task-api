package com.mkalki.cloudtaskapi.security;

import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class JwtServiceTest extends IntegrationTestConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JwtService jwtService;

    @Test
    void jwtSecretShouldBeAtLeast32Bytes() {
        // This test verifies that the JWT secret configured in the application
        // meets the minimum security requirement of 256 bits (32 bytes)
        
        byte[] secretBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        assertTrue(secretBytes.length >= 32, 
            "JWT secret must be at least 32 bytes. Current length: " + secretBytes.length);
    }

    @Test
    void contextLoadsWithValidJwtSecret() {
        // This test verifies that the application context loads successfully
        // with the configured JWT secret, meaning the @PostConstruct validation passed
        
        assertNotNull(jwtSecret, "JWT secret should be injected");
        assertTrue(jwtSecret.length() > 0, "JWT secret should not be empty");
    }

    @Test
    void jwtServiceShouldBeInjected() {
        // Verify that JwtService bean is properly initialized
        assertNotNull(jwtService, "JwtService should be injected");
    }
}
