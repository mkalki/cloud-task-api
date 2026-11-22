package com.mkalki.cloudtaskapi.job;

import java.util.EnumSet;
import java.util.Set;

public enum JobStatus {
    BLOCKED,
    PENDING,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    DEAD,
    CANCELLED,
    UPSTREAM_FAILED;

    private static final Set<JobStatus> TERMINAL =
            EnumSet.of(SUCCEEDED, DEAD, CANCELLED, UPSTREAM_FAILED);

    private static final Set<JobStatus> ACTIVE =
            EnumSet.of(BLOCKED, PENDING, QUEUED, RUNNING);

    /** DEAD counts as terminal, but an admin can re-open it (F4). */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** Statuses that count toward the per-user active-job cap. */
    public static Set<JobStatus> activeStatuses() {
        return ACTIVE;
    }
}
