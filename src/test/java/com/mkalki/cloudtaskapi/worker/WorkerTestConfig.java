package com.mkalki.cloudtaskapi.worker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Adds the test.controlled job type. It is a bean here, not a component, so other tests never see it. */
@TestConfiguration(proxyBeanMethods = false)
public class WorkerTestConfig {

    @Bean
    ControlledHandler controlledHandler() {
        return new ControlledHandler();
    }
}
