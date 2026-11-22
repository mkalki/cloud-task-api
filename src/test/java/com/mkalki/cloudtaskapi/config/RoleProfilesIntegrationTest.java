package com.mkalki.cloudtaskapi.config;

import com.mkalki.cloudtaskapi.config.role.ApiRoleConfig;
import com.mkalki.cloudtaskapi.config.role.SchedulerRoleConfig;
import com.mkalki.cloudtaskapi.config.role.WorkerRoleConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RoleProfilesIntegrationTest extends IntegrationTestConfig {

    @Autowired
    private ApplicationContext context;

    @Test
    void withNoActiveProfileAppShouldRunAsApiAndScheduler() {
        assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
        assertThat(context.getEnvironment().getDefaultProfiles())
                .containsExactly("api", "scheduler");

        assertThat(context.getBeanNamesForType(ApiRoleConfig.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(SchedulerRoleConfig.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(WorkerRoleConfig.class)).isEmpty();
    }
}
