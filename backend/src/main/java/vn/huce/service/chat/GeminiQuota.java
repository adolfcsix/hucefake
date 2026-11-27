package vn.huce.service.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded, per-account fixed-window quota shared by both assistant entry points. */
final class GeminiQuota {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_ACCOUNTS = 1500;
    private static final int MAX_REQUESTS = 8;

    private record Bucket(long expiresAt, int requests) {
    }

    private final LongSupplier clock;
    private final Map<Long, Bucket> buckets = new HashMap<>();

    GeminiQuota(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized boolean take(long actorId) {
        long now = clock.getAsLong();
        buckets.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        Bucket bucket = buckets.get(actorId);
        if (bucket == null) {
            if (buckets.size() >= MAX_ACCOUNTS) {
                return false;
            }
            bucket = new Bucket(now + WINDOW_MS, 0);
        }
        if (bucket.requests() >= MAX_REQUESTS) {
            return false;
        }
        buckets.put(actorId, new Bucket(bucket.expiresAt(), bucket.requests() + 1));
        return true;
    }
}
