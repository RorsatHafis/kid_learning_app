package com.platform.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The token bucket: capacity then refusal with the wait for the next token, refill up to capacity, one budget per
 * key, and idle buckets forgotten while spent ones are kept.
 */
class TokenBucketLimiterTests {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    @Test
    void aFullBucketAllowsItsCapacityThenRefusesWithTheWaitForTheNextToken() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(3, Duration.ofMinutes(1), now::get);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("a")).isZero();
        }
        // 3 per minute: one token every 20 s.
        assertThat(limiter.tryAcquire("a")).isEqualTo(Duration.ofSeconds(20));

        now.addAndGet(Duration.ofSeconds(5).toNanos());
        assertThat(limiter.tryAcquire("a")).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void tokensRefillOverTimeButNeverBeyondCapacity() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(2, Duration.ofMinutes(1), now::get);
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        now.addAndGet(Duration.ofSeconds(30).toNanos());
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isPositive();

        now.addAndGet(Duration.ofHours(5).toNanos());
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    void eachKeyHasItsOwnBudget() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(1, Duration.ofMinutes(1), now::get);

        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isPositive();
        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    void idleRefilledBucketsAreForgottenOnceTheBoundIsReachedButSpentOnesAreKept() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(1, Duration.ofMinutes(1), now::get);
        limiter.tryAcquire("spent");
        now.addAndGet(Duration.ofMinutes(2).toNanos());
        for (int i = 0; i < TokenBucketLimiter.MAX_KEYS; i++) {
            limiter.tryAcquire("client-" + i);
        }
        // "spent" has refilled and is dropped; the client-* buckets are spent and kept.
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(TokenBucketLimiter.MAX_KEYS);

        limiter.tryAcquire("newcomer");
        assertThat(limiter.tryAcquire("client-0")).isPositive();
    }

}
