package com.travel2go.backend.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * B5 acceptance: when notification-service is down, the fallback must let the
 * booking proceed (fire-and-forget confirmation), i.e. it must not throw.
 */
class NotificationClientFallbackFactoryTest {

    @Test
    void fallbackSwallowsFailureSoBookingIsUnaffected() {
        NotificationClientFallbackFactory factory = new NotificationClientFallbackFactory();
        NotificationClient fallback = factory.create(new RuntimeException("notification-service down"));

        assertThatCode(() ->
                fallback.sendBookingConfirmation(new NotificationClient.NotificationRequest()))
                .doesNotThrowAnyException();
    }
}
