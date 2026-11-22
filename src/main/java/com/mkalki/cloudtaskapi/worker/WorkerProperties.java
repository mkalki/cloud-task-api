package com.mkalki.cloudtaskapi.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under cloudtask.worker.*.
 *
 * @param enabled     starts one worker automatically in the worker profile (tests switch it off
 *                    and create workers themselves through {@link WorkerFactory})
 * @param concurrency jobs one worker runs at the same time
 * @param leaseMs     how long a claim holds a job before the lease must be renewed
 * @param pollMinMs   pause between polls right after work was found
 * @param pollMaxMs   longest pause between polls while the queue stays empty
 */
@ConfigurationProperties(prefix = "cloudtask.worker")
public record WorkerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("4") int concurrency,
        @DefaultValue("30000") long leaseMs,
        @DefaultValue("200") long pollMinMs,
        @DefaultValue("1000") long pollMaxMs
) {

    public WorkerProperties {
        if (concurrency < 1) {
            throw new IllegalArgumentException("cloudtask.worker.concurrency must be at least 1");
        }
        if (leaseMs < 100) {
            throw new IllegalArgumentException("cloudtask.worker.lease-ms must be at least 100");
        }
        if (pollMinMs < 1 || pollMaxMs < pollMinMs) {
            throw new IllegalArgumentException(
                    "cloudtask.worker.poll-min-ms must be at least 1 and not above poll-max-ms");
        }
    }

    /** The lease is renewed three times per lease period, so one missed renewal is survivable. */
    public long renewIntervalMs() {
        return leaseMs / 3;
    }
}
