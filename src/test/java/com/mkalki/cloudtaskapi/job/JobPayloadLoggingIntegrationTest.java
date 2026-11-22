package com.mkalki.cloudtaskapi.job;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payloads may contain personal data, so they must never reach the application log.
 * Runs our own code at DEBUG so a future debug line that prints a payload is caught too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.mkalki.cloudtaskapi=DEBUG")
class JobPayloadLoggingIntegrationTest extends JobApiTestSupport {

    private static final String SECRET = "secret-person-4f7c@example.com";

    @Test
    void payloadNeverAppearsInLogs(CapturedOutput output) throws Exception {
        String token = registerAndLogin("job-log");
        String body = "{\"type\": \"demo.work\", \"payload\": {\"email\": \"" + SECRET + "\"}}";

        String response = submit(token, body, "log-key").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");
        submit(token, body, "log-key").andExpect(status().isOk());
        getJob(token, id).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        cancelJob(token, id).andExpect(status().isOk());

        // Failure paths: too large for the validator, and an unknown type.
        submit(token, "{\"type\": \"demo.work\", \"payload\": {\"email\": \"" + SECRET + "\", \"pad\": \""
                + "x".repeat(66_000) + "\"}}").andExpect(status().isPayloadTooLarge());
        submit(token, "{\"type\": \"no.such.type\", \"payload\": {\"email\": \"" + SECRET + "\"}}")
                .andExpect(status().isBadRequest());

        // Proves the capture works: the transition log lines are there, the payload is not.
        assertThat(output.getAll()).contains("job submitted jobId=" + id);
        assertThat(output.getAll()).contains("job cancelled jobId=" + id);
        assertThat(output.getAll()).doesNotContain(SECRET);
    }
}
