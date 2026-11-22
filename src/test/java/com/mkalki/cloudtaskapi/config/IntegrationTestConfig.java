package com.mkalki.cloudtaskapi.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.TimeZone;

/**
 * Shared singleton containers: started once per test JVM and reused by every
 * integration test class, so Spring can also reuse its cached contexts.
 */
public abstract class IntegrationTestConfig {

    public static final String REDIS_IMAGE = "redis:7-alpine";
    public static final int REDIS_PORT = 6379;

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18")
                    .withDatabaseName("cloudtask")
                    .withUsername("test")
                    .withPassword("test")
                    .withEnv("TZ", "Asia/Kolkata");

    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT);

    static {
        Startables.deepStart(postgres, redis).join();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registerPostgres(registry);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }

    /** Lets tests that need their own Redis still share the Postgres container. */
    public static void registerPostgres(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }
}
