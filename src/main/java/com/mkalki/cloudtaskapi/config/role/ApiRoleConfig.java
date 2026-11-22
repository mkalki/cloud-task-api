package com.mkalki.cloudtaskapi.config.role;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Marker for the {@code api} role (REST API, WebSocket). Holds no beans yet.
 * Later features place API-only beans behind this profile.
 */
@Configuration(proxyBeanMethods = false)
@Profile("api")
public class ApiRoleConfig {
}
