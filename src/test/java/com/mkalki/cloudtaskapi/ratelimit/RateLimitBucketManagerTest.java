package com.mkalki.cloudtaskapi.ratelimit;

import com.mkalki.cloudtaskapi.config.IntegrationTestConfig;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "rate-limit.capacity=2",
        "rate-limit.refill-tokens=2",
        "rate-limit.refill-duration-minutes=1"
})
public class RateLimitBucketManagerTest extends IntegrationTestConfig {

    @Autowired
    private RateLimitBucketManager bucketManager;

    @Test
    void shouldCreateBucketForUser() {
        Bucket bucket = bucketManager.resolveBucket("user-1");

        assertNotNull(bucket);
    }

    @Test
    void shouldReturnSameBucketForSameUser() {
        Bucket firstBucket = bucketManager.resolveBucket("user-1");
        Bucket secondBucket = bucketManager.resolveBucket("user-1");

        assertSame(firstBucket,secondBucket);
    }

    @Test
    void shouldReturnDifferentBucketForDifferentUser() {
        Bucket aliceBucket = bucketManager.resolveBucket("alice");
        Bucket alice1Bucket = bucketManager.resolveBucket("alice1");

        assertNotSame(aliceBucket,alice1Bucket);
    }

    @Test
    void shouldRejectRequestWhenBucketIsEmpty() {

        Bucket bucket = bucketManager.resolveBucket("limit-test-user");

        assertTrue(bucket.tryConsume(1));
        assertTrue(bucket.tryConsume(1));
        assertFalse(bucket.tryConsume(1));
    }
}
