package com.mkalki.cloudtaskapi.worker;

import java.net.InetAddress;
import java.util.UUID;

/** Builds worker ids: the host name plus a short random suffix, so two workers on one host differ. */
final class WorkerIds {

    /** jobs.locked_by and job_attempts.worker_id are VARCHAR(100). */
    private static final int MAX_HOST_LENGTH = 80;

    private WorkerIds() {
    }

    static String newId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "worker";
        }
        if (host.length() > MAX_HOST_LENGTH) {
            host = host.substring(0, MAX_HOST_LENGTH);
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
