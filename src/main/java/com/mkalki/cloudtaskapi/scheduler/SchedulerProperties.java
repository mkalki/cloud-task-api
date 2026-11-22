package com.mkalki.cloudtaskapi.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings under cloudtask.scheduler.*.
 *
 * @param enabled            turns the background timers off (tests then call the beans directly)
 * @param pollInterval       pause between two promote runs
 * @param promoteBatchSize   most jobs moved from scheduled to ready in one run
 * @param reconcileInterval  pause between two reconcile runs
 * @param reconcileBatchSize rows read from PostgreSQL per page while reconciling
 */
@ConfigurationProperties(prefix = "cloudtask.scheduler")
public record SchedulerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PT1S") Duration pollInterval,
        @DefaultValue("100") int promoteBatchSize,
        @DefaultValue("PT60S") Duration reconcileInterval,
        @DefaultValue("500") int reconcileBatchSize
) {
}
