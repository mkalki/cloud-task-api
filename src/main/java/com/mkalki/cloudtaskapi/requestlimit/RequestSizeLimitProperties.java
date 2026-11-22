package com.mkalki.cloudtaskapi.requestlimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Objects;

/**
 * Settings under {@code cloudtask.request-size.*}: the largest request body accepted per path.
 *
 * <p>Example:
 * <pre>
 * cloudtask.request-size.rules[0].method=POST
 * cloudtask.request-size.rules[0].path=/api/v1/jobs
 * cloudtask.request-size.rules[0].max-size=72KB
 * </pre>
 * Setting any rule replaces the default list, so re-list the jobs rule when adding others.
 *
 * @param rules body limits; the first rule that matches a request wins
 */
@ConfigurationProperties(prefix = "cloudtask.request-size")
public record RequestSizeLimitProperties(List<Rule> rules) {

    /**
     * 64 KB of job payload plus room for the other submit fields and JSON whitespace.
     * The exact payload limit is still checked later by the job validator.
     */
    static final Rule DEFAULT_JOB_SUBMIT_RULE =
            new Rule("POST", "/api/v1/jobs", DataSize.ofKilobytes(72));

    public RequestSizeLimitProperties {
        rules = (rules == null) ? List.of(DEFAULT_JOB_SUBMIT_RULE) : List.copyOf(rules);
    }

    /**
     * @param method  HTTP method such as POST; null matches every method
     * @param path    Ant-style path pattern, for example /api/v1/jobs or /api/v1/tasks/**
     * @param maxSize largest body accepted, for example 72KB
     */
    public record Rule(String method, String path, DataSize maxSize) {
        public Rule {
            Objects.requireNonNull(path, "cloudtask.request-size rule needs a path");
            Objects.requireNonNull(maxSize, "cloudtask.request-size rule needs a max-size");
        }
    }
}
