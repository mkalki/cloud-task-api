package com.mkalki.cloudtaskapi.task;

import com.jayway.jsonpath.JsonPath;
import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskIntegrationTest extends IntegrationTestConfig {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticatedUserShouldCreateTask() throws Exception {

        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "task-test-user",
                                  "password": "password123"
                                }
                                """)
        ).andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "username": "task-test-user",
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
                post("/tasks")
                        .header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Integration test task"
                                }
                                """)
        ).andExpect(status().isCreated());
    }

    @Test
    void userCannotAccessAnotherUsersTask() throws Exception {

        // Register User A
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "task-owner-user",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User A
        String userALoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "username": "task-owner-user",
                              "password": "password123"
                            }
                            """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userAAccessToken = JsonPath.read(
                userALoginResponse,
                "$.accessToken"
        );

        // User A creates a task
        String taskResponse = mockMvc.perform(
                        post("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + userAAccessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "title": "Private task"
                            }
                            """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number taskId = JsonPath.read(
                taskResponse,
                "$.id"
        );

        // Register User B
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "another-task-user",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User B
        String userBLoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "username": "another-task-user",
                              "password": "password123"
                            }
                            """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userBAccessToken = JsonPath.read(
                userBLoginResponse,
                "$.accessToken"
        );

        // User B tries to access User A's task
        mockMvc.perform(
                get("/tasks/" + taskId.longValue())
                        .header(
                                "Authorization",
                                "Bearer " + userBAccessToken
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void userCannotUpdateAnotherUsersTask() throws Exception {

        // Register User A
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "update-owner-user",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User A
        String userALoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "update-owner-user",
                                      "password": "password123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userAAccessToken = JsonPath.read(
                userALoginResponse,
                "$.accessToken"
        );

        // User A creates a task
        String taskResponse = mockMvc.perform(
                        post("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + userAAccessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "title": "Original task"
                                    }
                                    """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number taskId = JsonPath.read(
                taskResponse,
                "$.id"
        );

        // Register User B
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "update-another-user",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User B
        String userBLoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "update-another-user",
                                      "password": "password123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userBAccessToken = JsonPath.read(
                userBLoginResponse,
                "$.accessToken"
        );

        // User B tries to update User A's task
        mockMvc.perform(
                put("/tasks/" + taskId.longValue())
                        .header(
                                "Authorization",
                                "Bearer " + userBAccessToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "title": "Hacked task"
                            }
                            """)
        ).andExpect(status().isForbidden());
    }

    @Test
    void userCannotDeleteAnotherUsersTask() throws Exception {

        // Register User A
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "delete-owner-user",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User A
        String userALoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "delete-owner-user",
                                      "password": "password123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userAAccessToken = JsonPath.read(
                userALoginResponse,
                "$.accessToken"
        );

        // User A creates a task
        String taskResponse = mockMvc.perform(
                        post("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + userAAccessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "title": "Task that belongs to User A"
                                    }
                                    """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number taskId = JsonPath.read(
                taskResponse,
                "$.id"
        );

        // Register User B
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "delete-another-user",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User B
        String userBLoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "delete-another-user",
                                      "password": "password123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userBAccessToken = JsonPath.read(
                userBLoginResponse,
                "$.accessToken"
        );

        // User B tries to delete User A's task
        mockMvc.perform(
                delete("/tasks/" + taskId.longValue())
                        .header(
                                "Authorization",
                                "Bearer " + userBAccessToken
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void taskOwnerShouldUpdateOwnTask() throws Exception {

        // Register user
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "task-update-owner",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login user
        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "task-update-owner",
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

        // Create task
        String taskResponse = mockMvc.perform(
                        post("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "title": "Original task title"
                                    }
                                    """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number taskId = JsonPath.read(
                taskResponse,
                "$.id"
        );

        // Owner updates own task
        mockMvc.perform(
                        put("/tasks/" + taskId.longValue())
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                              "title": "Updated task title"
                            }
                            """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated task title"));
    }

    @Test
    void taskOwnerShouldDeleteOwnTask() throws Exception {

        // Register user
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "task-delete-owner",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login user
        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "task-delete-owner",
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

        // Create task
        String taskResponse = mockMvc.perform(
                        post("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "title": "Task to delete"
                                    }
                                    """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number taskId = JsonPath.read(
                taskResponse,
                "$.id"
        );

        // Owner deletes own task
        mockMvc.perform(
                delete("/tasks/" + taskId.longValue())
                        .header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
        ).andExpect(status().isNoContent());

        // Verify deleted task is no longer accessible
        mockMvc.perform(
                get("/tasks/" + taskId.longValue())
                        .header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
        ).andExpect(status().isNotFound());
    }

    @Test
    void taskOwnerShouldAccessOwnTask() throws Exception {

        // Register user
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "task-get-owner",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login user
        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "task-get-owner",
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

        // Create task
        String taskResponse = mockMvc.perform(
                        post("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "title": "My private task"
                                    }
                                    """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number taskId = JsonPath.read(
                taskResponse,
                "$.id"
        );

        // Owner retrieves own task
        mockMvc.perform(
                        get("/tasks/" + taskId.longValue())
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.longValue()))
                .andExpect(jsonPath("$.title").value("My private task"));
    }

    @Test
    void userShouldOnlySeeOwnTasks() throws Exception {

        // Register User A
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "task-list-user-a",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User A
        String userALoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "task-list-user-a",
                                      "password": "password123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userAAccessToken = JsonPath.read(
                userALoginResponse,
                "$.accessToken"
        );

        // User A creates a task
        mockMvc.perform(
                post("/tasks")
                        .header(
                                "Authorization",
                                "Bearer " + userAAccessToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "title": "User A private task"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Register User B
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "task-list-user-b",
                              "password": "password123"
                            }
                            """)
        ).andExpect(status().isCreated());

        // Login User B
        String userBLoginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "username": "task-list-user-b",
                                      "password": "password123"
                                    }
                                    """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userBAccessToken = JsonPath.read(
                userBLoginResponse,
                "$.accessToken"
        );

        // User B creates a task
        mockMvc.perform(
                post("/tasks")
                        .header(
                                "Authorization",
                                "Bearer " + userBAccessToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "title": "User B private task"
                            }
                            """)
        ).andExpect(status().isCreated());

        // User A retrieves the task list
        mockMvc.perform(
                        get("/tasks")
                                .header(
                                        "Authorization",
                                        "Bearer " + userAAccessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title")
                        .value(org.hamcrest.Matchers.hasItem("User A private task")))
                .andExpect(jsonPath("$.content[*].title")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem("User B private task")
                        )));
    }

    @Test
    void unauthenticatedUserShouldNotAccessTasks() throws Exception {

        mockMvc.perform(
                get("/tasks")
        ).andExpect(status().isUnauthorized());
    }
}