package com.mkalki.cloudtaskapi.job.handler;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Allowlist of job types. A job can only be submitted for a type registered here. */
@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers = new HashMap<>();

    public JobHandlerRegistry(List<JobHandler> handlerBeans) {
        for (JobHandler handler : handlerBeans) {
            JobHandler previous = handlers.put(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two job handlers registered for type " + handler.type());
            }
        }
    }

    public boolean isRegistered(String type) {
        return handlers.containsKey(type);
    }

    public Optional<JobHandler> find(String type) {
        return Optional.ofNullable(handlers.get(type));
    }

    public Set<String> types() {
        return Set.copyOf(handlers.keySet());
    }
}
