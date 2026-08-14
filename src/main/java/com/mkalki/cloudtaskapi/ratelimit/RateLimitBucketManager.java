package com.mkalki.cloudtaskapi.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitBucketManager {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${rate-limit.capacity}")
    private long capacity;

    @Value("${rate-limit.refill-tokens}")
    private long refillTokens;

    @Value("${rate-limit.refill-duration-minutes}")
    private long refillDurationMinutes;

    private Bucket createBucket(){
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofMinutes(refillDurationMinutes))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public Bucket resolveBucket(String userId){
        return buckets.computeIfAbsent(userId, k -> createBucket());
    }
}

