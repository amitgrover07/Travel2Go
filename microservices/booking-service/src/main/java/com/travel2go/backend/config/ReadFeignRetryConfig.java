package com.travel2go.backend.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * B5: retry-with-backoff applied ONLY to the idempotent read clients
 * (SettingsClient, PackageClient) via @FeignClient(configuration = ...).
 *
 * Deliberately NOT annotated @Configuration and NOT component-scanned, so it does
 * not become global. Non-idempotent writes - booking confirmation, leg booking
 * (money path), and OTP - must never auto-retry, or a transient blip could double
 * a booking or an OTP. They keep Feign's default Retryer.NEVER_RETRY.
 */
public class ReadFeignRetryConfig {

    @Bean
    public Retryer retryer() {
        // up to 3 attempts: 100ms initial backoff, capped at 1s
        return new Retryer.Default(100, 1000, 3);
    }
}
