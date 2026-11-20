package com.travel2go.backend.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * A notification outage must never block the disruption rescue itself: the leg is
 * still rebooked/refunded and the event emitted. The failed notice is logged for retry.
 */
@Slf4j
@Component
public class DisruptionNotificationClientFallbackFactory
        implements FallbackFactory<DisruptionNotificationClient> {

    @Override
    public DisruptionNotificationClient create(Throwable cause) {
        return request -> log.error("Disruption notice for leg {} (outcome {}) failed to send: {}",
                request.legId, request.outcome, cause.getMessage());
    }
}
