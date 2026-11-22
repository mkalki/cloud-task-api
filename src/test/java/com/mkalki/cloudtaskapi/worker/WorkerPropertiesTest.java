package com.mkalki.cloudtaskapi.worker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerPropertiesTest {

    @Test
    void leaseIsRenewedThreeTimesPerLeasePeriod() {
        assertThat(new WorkerProperties(true, 4, 30_000, 200, 1000).renewIntervalMs()).isEqualTo(10_000);
        assertThat(new WorkerProperties(true, 1, 600, 20, 100).renewIntervalMs()).isEqualTo(200);
    }

    @Test
    void concurrencyBelowOneIsRejected() {
        assertThatThrownBy(() -> new WorkerProperties(true, 0, 30_000, 200, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("concurrency");
    }

    @Test
    void aTooShortLeaseIsRejected() {
        assertThatThrownBy(() -> new WorkerProperties(true, 4, 99, 200, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease-ms");
    }

    @Test
    void pollMaxBelowPollMinIsRejected() {
        assertThatThrownBy(() -> new WorkerProperties(true, 4, 30_000, 500, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("poll");
    }
}
