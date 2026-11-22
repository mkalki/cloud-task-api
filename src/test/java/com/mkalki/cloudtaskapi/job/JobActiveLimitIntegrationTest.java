package com.mkalki.cloudtaskapi.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "cloudtask.jobs.max-active-per-user=2")
class JobActiveLimitIntegrationTest extends JobApiTestSupport {

    @Test
    void submittingBeyondTheActiveLimitReturns429() throws Exception {
        String token = registerAndLogin("job-cap");
        submit(token, DEMO_JOB).andExpect(status().isCreated());
        submit(token, DEMO_JOB).andExpect(status().isCreated());

        submit(token, DEMO_JOB)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Active job limit of 2 reached"));
    }

    @Test
    void cancellingAJobFreesCapacity() throws Exception {
        String token = registerAndLogin("job-cap-free");
        String first = submitOk(token, DEMO_JOB);
        submitOk(token, DEMO_JOB);
        submit(token, DEMO_JOB).andExpect(status().isTooManyRequests());

        cancelJob(token, first).andExpect(status().isOk());

        submit(token, DEMO_JOB).andExpect(status().isCreated());
    }

    @Test
    void theLimitIsPerUser() throws Exception {
        String alice = registerAndLogin("job-cap-a");
        String bob = registerAndLogin("job-cap-b");
        submitOk(alice, DEMO_JOB);
        submitOk(alice, DEMO_JOB);
        submit(alice, DEMO_JOB).andExpect(status().isTooManyRequests());

        submit(bob, DEMO_JOB).andExpect(status().isCreated());
    }

    @Test
    void replayingAnIdempotencyKeyStillWorksAtTheLimit() throws Exception {
        String token = registerAndLogin("job-cap-idem");
        submit(token, DEMO_JOB, "same").andExpect(status().isCreated());
        submitOk(token, DEMO_JOB);

        submit(token, DEMO_JOB, "same").andExpect(status().isOk());
    }
}
