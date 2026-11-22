package com.mkalki.cloudtaskapi.job.handler;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** What a handler receives when it runs a job. Workers build it in F3. */
public record JobContext(UUID jobId, String type, int attempt, JsonNode payload) {
}
