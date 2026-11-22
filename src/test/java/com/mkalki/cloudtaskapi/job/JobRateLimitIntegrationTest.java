package com.mkalki.cloudtaskapi.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

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
class JobRateLimitIntegrationTest extends JobApiTestSupport {

    @Test
    void jobSubmissionIsRateLimitedPerUser() throws Exception {
        String token = registerAndLogin("job-rate");

        submit(token, DEMO_JOB).andExpect(status().isCreated());
        submit(token, DEMO_JOB).andExpect(status().isCreated());

        submit(token, DEMO_JOB)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value("Too many requests"));
    }

    @Test
    void anotherUsersBucketIsNotAffected() throws Exception {
        String noisy = registerAndLogin("job-rate-noisy");
        String quiet = registerAndLogin("job-rate-quiet");
        submit(noisy, DEMO_JOB);
        submit(noisy, DEMO_JOB);
        submit(noisy, DEMO_JOB).andExpect(status().isTooManyRequests());

        submit(quiet, DEMO_JOB).andExpect(status().isCreated());
    }
}
