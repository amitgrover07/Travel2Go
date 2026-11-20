package com.travel2go.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Strict per-IP fixed-window rate limiter for the public endpoint (F1 guardrail).
 * In-memory and dependency-free; keyed by the already-hashed IP so no raw address
 * is retained. Single-instance scope is sufficient for v1 - a shared limiter can
 * drop in later without changing callers.
 */
@Component
public class IpRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public IpRateLimiter(
            @Value("${waitlist.ratelimit.max-requests:20}") int maxRequests,
            @Value("${waitlist.ratelimit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    /** @return true if the caller is within budget; false if it must be throttled (429). */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMillis >= windowMillis) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() <= maxRequests;
    }

    private static final class Window {
        final long startMillis;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
