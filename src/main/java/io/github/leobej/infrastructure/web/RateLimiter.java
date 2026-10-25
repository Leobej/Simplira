package io.github.leobej.infrastructure.web;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

// Token buckets in memory: capacity requests at once, refilling evenly over the window. Enough for
// one instance; several would need the buckets in a shared store (Redis) or they each allow the
// full rate.
public class RateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    public RateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    // 0 if the request may go ahead, otherwise how many whole seconds until it could.
    public long tryAcquire(String key, int capacity, Duration window) {
        long now = nanoTime.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        return bucket.take(capacity, window.toNanos(), now);
    }

    // A bucket idle for a whole window is full again - the same as having none - so it can go.
    public void evictIdle(Duration longestWindow) {
        long now = nanoTime.getAsLong();
        buckets.values().removeIf(bucket -> bucket.idleFor(now) >= longestWindow.toNanos());
    }

    int size() {
        return buckets.size();
    }

    private static final class Bucket {
        private double tokens;
        private long last;

        Bucket(int capacity, long now) {
            this.tokens = capacity;
            this.last = now;
        }

        synchronized long take(int capacity, long windowNanos, long now) {
            tokens = Math.min(capacity, tokens + (now - last) * (double) capacity / windowNanos);
            last = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            double nanosUntilOne = (1 - tokens) * windowNanos / capacity;
            return Math.max(1, (long) Math.ceil(nanosUntilOne / 1_000_000_000d));
        }

        synchronized long idleFor(long now) {
            return now - last;
        }
    }
}
