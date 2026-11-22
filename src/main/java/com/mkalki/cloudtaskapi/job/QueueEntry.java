package com.mkalki.cloudtaskapi.job;

import java.time.Instant;
import java.util.UUID;

/** The few job columns the queue reconciler needs, so it never loads payloads. */
public interface QueueEntry {

    UUID getId();

    int getPriority();

    Instant getRunAt();
}
