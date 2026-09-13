package com.travel2go.backend.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * B5: booking -> notification is fire-and-forget. The booking is already persisted
 * before the confirmation is sent, so a notification-service outage must NOT fail
 * the booking. This fallback logs and swallows, and combined with the circuit
 * breaker it fast-fails instead of hanging the booking thread while notification
 * is down.
 *
 * Only these notification calls get a swallowing fallback. The money path
 * (trip -> booking) and the reads (package/settings) intentionally have none, so
 * they fail loudly and leave their callers to recover.
 */
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationClientFallbackFactory.class);

    @Override
    public NotificationClient create(Throwable cause) {
        return new NotificationClient() {
            @Override
            public void sendBookingConfirmation(NotificationRequest request) {
                log.warn("notification-service unavailable; booking confirmation not sent (booking is unaffected). Reason: {}",
                        cause.toString());
            }

            @Override
            public void sendLegBookingConfirmation(LegBookingConfirmationRequest request) {
                log.warn("notification-service unavailable; leg-booking confirmation not sent (booking is unaffected). Reason: {}",
                        cause.toString());
            }
        };
    }
}
