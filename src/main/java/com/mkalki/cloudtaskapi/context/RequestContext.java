package com.mkalki.cloudtaskapi.context;

public record RequestContext(
        Long userId,
        String ipAddress,
        String userAgent
) {
}
