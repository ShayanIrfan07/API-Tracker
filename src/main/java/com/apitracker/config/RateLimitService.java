package com.apitracker.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory token-bucket rate limiter (per JVM / client key).
 */
public class RateLimitService {

    private final RateLimitProperties properties;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public boolean tryConsume(String bucketKey, int permitsPerMinute) {
        if (!properties.enabled()) {
            return true;
        }
        TokenBucket bucket = buckets.computeIfAbsent(
                bucketKey,
                key -> new TokenBucket(permitsPerMinute, permitsPerMinute));
        return bucket.tryConsume();
    }

    public int apiPermitsPerMinute() {
        return properties.apiPermitsPerMinute();
    }

    public int loginPermitsPerMinute() {
        return properties.loginPermitsPerMinute();
    }

    public int checkNowPermitsPerMinute() {
        return properties.checkNowPermitsPerMinute();
    }

    static final class TokenBucket {
        private final double capacity;
        private final double refillPerNanos;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, int refillPerMinute) {
            this.capacity = capacity;
            this.refillPerNanos = refillPerMinute / 60_000_000_000.0d;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens < 1.0d) {
                return false;
            }
            tokens -= 1.0d;
            return true;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            tokens = Math.min(capacity, tokens + elapsed * refillPerNanos);
            lastRefillNanos = now;
        }
    }
}
