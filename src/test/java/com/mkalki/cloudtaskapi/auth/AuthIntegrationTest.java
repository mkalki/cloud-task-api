package com.mkalki.cloudtaskapi.auth;

import com.jayway.jsonpath.JsonPath;
import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestConfig {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerShouldReturnCreated() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "integration-test-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());
    }

    @Test
    void loginShouldReturnOk() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "login-test-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "login-test-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isOk());
    }

    @Test
    void loginWithInvalidPasswordShouldReturnUnauthorized() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "invalid-login-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "invalid-login-user",
                                  "password": "wrong-password"
                                }
                                """)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void registerWithDuplicateUsernameShouldReturnConflict() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "duplicate-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "duplicate-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isConflict());
    }

    @Test
    void refreshTokenShouldReturnOk() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "refresh-test-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "username": "refresh-test-user",
                                  "password": "password123"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = JsonPath.read(
                loginResponse,
                "$.refreshToken"
        );

        mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void invalidRefreshTokenShouldReturnUnauthorized() throws Exception {

        mockMvc.perform(
                post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "invalid-refresh-token"
                                }
                                """)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void reusedRefreshTokenShouldReturnUnauthorized() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "refresh-reuse-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "username": "refresh-reuse-user",
                                  "password": "password123"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String oldRefreshToken = JsonPath.read(
                loginResponse,
                "$.refreshToken"
        );

        mockMvc.perform(
                post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken))
        ).andExpect(status().isOk());

        mockMvc.perform(
                post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(oldRefreshToken))
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void accessingProtectedEndpointWithValidTokenShouldReturnOk() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "jwt-test-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "username": "jwt-test-user",
                                  "password": "password123"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = JsonPath.read(
                loginResponse,
                "$.accessToken"
        );

        mockMvc.perform(
                get("/tasks")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
        ).andExpect(status().isOk());
    }

    @Test
    void accessingProtectedEndpointWithInvalidTokenShouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(
                get("/tasks")
                        .header(
                                "Authorization",
                                "Bearer invalid-jwt-token"
                        )
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldRevokeRefreshToken() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "logout-test-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "username": "logout-test-user",
                                  "password": "password123"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = JsonPath.read(
                loginResponse,
                "$.refreshToken"
        );

        mockMvc.perform(
                post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken))
        ).andExpect(status().isNoContent());

        mockMvc.perform(
                post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken))
        ).andExpect(status().isUnauthorized());
    }
}