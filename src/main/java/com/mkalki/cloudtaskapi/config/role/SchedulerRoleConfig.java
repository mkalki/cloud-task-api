package com.mkalki.cloudtaskapi.config.role;

import com.mkalki.cloudtaskapi.scheduler.SchedulerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The {@code scheduler} role (promotes due jobs and reconciles the queues; the reaper arrives in F4).
 * It switches on Spring's @Scheduled support and binds cloudtask.scheduler.*. The beans themselves
 * live in the scheduler package.
 */
@Configuration(proxyBeanMethods = false)
@Profile("scheduler")
@EnableScheduling
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerRoleConfig {
}
