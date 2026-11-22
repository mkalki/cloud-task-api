package com.mkalki.cloudtaskapi.job.handler;

import org.springframework.stereotype.Component;

/**
 * Placeholder handler so {@code demo.work} is an accepted job type in F1.
 * Nothing executes yet. F3 replaces the body with the real demo behavior.
 */
@Component
public class DemoWorkHandler implements JobHandler {

    public static final String TYPE = "demo.work";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobContext ctx) {
        // Intentionally empty in F1.
    }
}
