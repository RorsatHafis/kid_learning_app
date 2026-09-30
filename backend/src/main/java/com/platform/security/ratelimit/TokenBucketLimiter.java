package com.platform.security.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory token buckets, one per key. A bucket holds up to {@code capacity}
 * tokens and refills continuously at {@code capacity} per {@code period}; each
 * request takes one token or is refused with the time until the next token.
 *
 * <p>State lives in this JVM only. With more than one backend instance each keeps
 * its own budget, so the effective limit multiplies by the instance count - accepted
 * until the deployment topology exists (OD-12); a shared store would replace this.
 *
 * <p>Memory is bounded: once {@link #MAX_KEYS} keys are tracked, buckets that have
 * refilled completely (indistinguishable from a fresh one) are dropped. If every
 * tracked key is still active the map may exceed the bound briefly rather than
 * forget an active client's spent budget.
 */
public final class TokenBucketLimiter {

    static final int MAX_KEYS = 50_000;

    private final int capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(int capacity, Duration period, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.tokensPerNano = capacity / (double) period.toNanos();
        this.nanoClock = nanoClock;
    }

    /** @return {@link Duration#ZERO} when allowed, otherwise how long until a token is available. */
    public Duration tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        if (buckets.size() >= MAX_KEYS) {
            buckets.values().removeIf(bucket -> bucket.isFull(now));
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(now)).tryAcquire(now);
    }

    int trackedKeys() {
        return buckets.size();
    }

    private final class Bucket {

        private double tokens = capacity;
        private long lastRefill;

        Bucket(long now) {
            this.lastRefill = now;
        }

        synchronized Duration tryAcquire(long now) {
            refill(now);
            if (tokens >= 1) {
                tokens -= 1;
                return Duration.ZERO;
            }
            return Duration.ofNanos((long) Math.ceil((1 - tokens) / tokensPerNano));
        }

        synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
            lastRefill = now;
        }

    }

}
