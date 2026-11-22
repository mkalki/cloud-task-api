package com.mkalki.cloudtaskapi.job;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code cloudtask.jobs.*}.
 *
 * @param maxActivePerUser    most BLOCKED, PENDING, QUEUED or RUNNING jobs one user may hold
 * @param maxPayloadBytes     largest accepted payload, measured as serialized JSON
 * @param maxScheduleAheadDays furthest in the future a job may be scheduled
 */
@ConfigurationProperties(prefix = "cloudtask.jobs")
public record JobProperties(
        @DefaultValue("1000") int maxActivePerUser,
        @DefaultValue("65536") int maxPayloadBytes,
        @DefaultValue("365") int maxScheduleAheadDays
) {
}
