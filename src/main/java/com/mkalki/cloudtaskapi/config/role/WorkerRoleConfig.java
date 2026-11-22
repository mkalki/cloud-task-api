package com.mkalki.cloudtaskapi.config.role;

import com.mkalki.cloudtaskapi.worker.Worker;
import com.mkalki.cloudtaskapi.worker.WorkerFactory;
import com.mkalki.cloudtaskapi.worker.WorkerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The {@code worker} role (claims and executes jobs). It binds cloudtask.worker.* and starts one
 * {@link Worker}. The worker is a SmartLifecycle bean: Spring starts it after the context is ready
 * and stops it on shutdown. Tests set cloudtask.worker.enabled=false and create workers themselves.
 */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerRoleConfig {

    @Bean
    @ConditionalOnProperty(prefix = "cloudtask.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    Worker worker(WorkerFactory factory) {
        return factory.create();
    }
}
