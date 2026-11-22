package com.mkalki.cloudtaskapi.worker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobRunServiceTest {

    @Test
    void anErrorOverFourKilobytesIsCutToFourKilobytes() {
        String longError = "x".repeat(JobRunService.MAX_ERROR_CHARS + 500);

        assertThat(JobRunService.truncate(longError)).hasSize(JobRunService.MAX_ERROR_CHARS);
        assertThat(JobRunService.MAX_ERROR_CHARS).isEqualTo(4096);
    }

    @Test
    void shortAndMissingErrorsAreKept() {
        assertThat(JobRunService.truncate("boom")).isEqualTo("boom");
        assertThat(JobRunService.truncate(null)).isNull();
    }
}
