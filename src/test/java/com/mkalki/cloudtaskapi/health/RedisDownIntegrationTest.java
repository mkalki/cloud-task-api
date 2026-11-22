package com.mkalki.cloudtaskapi.health;

import com.jayway.jsonpath.JsonPath;
import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses its own Redis container (not the shared one) because the test stops it.
 * Postgres is still the shared container.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedisDownIntegrationTest {

    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse(IntegrationTestConfig.REDIS_IMAGE))
                    .withExposedPorts(IntegrationTestConfig.REDIS_PORT);

    static {
        redis.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        IntegrationTestConfig.registerPostgres(registry);

        // Read host and port now: after redis.stop() the container can no longer report them.
        String host = redis.getHost();
        Integer port = redis.getMappedPort(IntegrationTestConfig.REDIS_PORT);
        registry.add("spring.data.redis.host", () -> host);
        registry.add("spring.data.redis.port", () -> port);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenRedisStopsTasksStillWorkAndRedisHealthIsDown() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.redis.status").value("UP"));

        redis.stop();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("DOWN"));

        String accessToken = registerAndLogin("redis-down-user");

        mockMvc.perform(
                post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Task created while Redis is down"
                                }
                                """)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/tasks")
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title")
                        .value("Task created while Redis is down"));
    }

    private String registerAndLogin(String username) throws Exception {
        String credentials = """
                {
                  "username": "%s",
                  "password": "Password123"
                }
                """.formatted(username);

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials)
        ).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(credentials)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(loginResponse, "$.accessToken");
    }
}
