package com.mkalki.cloudtaskapi.job;

import com.jayway.jsonpath.JsonPath;
import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared helpers for the job API integration tests. Subclasses add the Spring annotations. */
public abstract class JobApiTestSupport extends IntegrationTestConfig {

    protected static final String DEMO_JOB = """
            {"type": "demo.work", "payload": {"durationMs": 10}}
            """;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Registers a new user with a unique name and returns an access token. */
    protected String registerAndLogin(String prefix) throws Exception {
        String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String credentials = """
                {"username": "%s", "password": "Password123"}
                """.formatted(username);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated());

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(loginResponse, "$.accessToken");
    }

    protected ResultActions submit(String token, String body) throws Exception {
        return submit(token, body, null);
    }

    protected ResultActions submit(String token, String body, String idempotencyKey) throws Exception {
        var request = post("/api/v1/jobs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    /** Submits a job that must succeed and returns its id. */
    protected String submitOk(String token, String body) throws Exception {
        String response = submit(token, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    protected ResultActions getJob(String token, String id) throws Exception {
        return mockMvc.perform(get("/api/v1/jobs/" + id)
                .header("Authorization", "Bearer " + token));
    }

    protected ResultActions cancelJob(String token, String id) throws Exception {
        return mockMvc.perform(post("/api/v1/jobs/" + id + "/cancel")
                .header("Authorization", "Bearer " + token));
    }

    protected long userIdOf(String token) {
        return jdbc.queryForObject(
                "select id from users where username = ?", Long.class, usernameOf(token));
    }

    protected long jobCountFor(String token) {
        return jdbc.queryForObject(
                "select count(*) from jobs where owner_id = ?", Long.class, userIdOf(token));
    }

    /**
     * True while some other database session is blocked on a lock. Tests use it to know that
     * a request has reached a statement that waits for an uncommitted transaction.
     */
    protected boolean someSessionIsWaitingOnALock() {
        Long waiting = jdbc.queryForObject("""
                select count(*) from pg_stat_activity
                 where wait_event_type = 'Lock'
                   and pid <> pg_backend_pid()
                """, Long.class);
        return waiting != null && waiting > 0;
    }

    /** The JWT subject is the username, so it is the middle part of the token, base64url-encoded. */
    private String usernameOf(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return JsonPath.read(payload, "$.sub");
    }
}
