package com.mkalki.cloudtaskapi.config.role;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Marker for the {@code worker} role (claims and executes jobs).
 * Holds no beans yet; worker logic arrives in F3.
 */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
public class WorkerRoleConfig {
}
