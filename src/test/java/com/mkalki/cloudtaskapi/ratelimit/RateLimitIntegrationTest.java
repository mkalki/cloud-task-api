package com.mkalki.cloudtaskapi.ratelimit;

import com.jayway.jsonpath.JsonPath;
import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.capacity=2",
        "rate-limit.refill-tokens=2",
        "rate-limit.refill-duration-minutes=1"
})
class RateLimitIntegrationTest extends IntegrationTestConfig {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticatedUserShouldBeRateLimitedAfterBucketIsExhausted()
            throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "rate-limit-integration-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "username": "rate-limit-integration-user",
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

        mockMvc.perform(
                get("/tasks")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
        ).andExpect(status().isOk());

        mockMvc.perform(
                        get("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error")
                        .value("Too many requests"));
    }
}