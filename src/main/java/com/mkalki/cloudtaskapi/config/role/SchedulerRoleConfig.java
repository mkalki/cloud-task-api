package com.mkalki.cloudtaskapi.config.role;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Marker for the {@code scheduler} role (promotes due jobs, reaper, reconciler).
 * Holds no beans yet; scheduling logic arrives in F2.
 */
@Configuration(proxyBeanMethods = false)
@Profile("scheduler")
public class SchedulerRoleConfig {
}
