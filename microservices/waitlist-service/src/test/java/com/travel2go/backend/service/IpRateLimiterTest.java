package com.travel2go.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpRateLimiterTest {

    @Test
    void allowsUpToLimitThenThrottles() {
        IpRateLimiter limiter = new IpRateLimiter(3, 60);
        assertThat(limiter.tryAcquire("ip-a")).isTrue();
        assertThat(limiter.tryAcquire("ip-a")).isTrue();
        assertThat(limiter.tryAcquire("ip-a")).isTrue();
        assertThat(limiter.tryAcquire("ip-a")).isFalse();
    }

    @Test
    void limitsArePerKey() {
        IpRateLimiter limiter = new IpRateLimiter(1, 60);
        assertThat(limiter.tryAcquire("ip-a")).isTrue();
        assertThat(limiter.tryAcquire("ip-a")).isFalse();
        // a different IP has its own budget
        assertThat(limiter.tryAcquire("ip-b")).isTrue();
    }

    @Test
    void windowResetsAfterItElapses() throws InterruptedException {
        IpRateLimiter limiter = new IpRateLimiter(1, 1); // 1 request per 1 second
        assertThat(limiter.tryAcquire("ip-a")).isTrue();
        assertThat(limiter.tryAcquire("ip-a")).isFalse();
        Thread.sleep(1100);
        assertThat(limiter.tryAcquire("ip-a")).isTrue();
    }
}
