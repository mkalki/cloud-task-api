package com.mkalki.cloudtaskapi.worker;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Real PostgreSQL and Redis, all three roles active, no worker started by Spring
 * (cloudtask.worker.enabled=false in the test properties) and no scheduler timers.
 * Tests create workers with WorkerFactory and call the promoter themselves.
 * All classes using it share one cached Spring context.
 * The name does not end in "Test" on purpose, so Surefire does not treat it as a test class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"api", "scheduler", "worker"})
@Import(WorkerTestConfig.class)
public @interface WorkerTestContext {
}
