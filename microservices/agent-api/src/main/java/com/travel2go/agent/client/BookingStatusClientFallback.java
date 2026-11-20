package com.travel2go.agent.client;

import com.travel2go.backend.model.Booking;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Graceful degradation: when booking-service is unreachable, return null so the
 * journey endpoint reports available=false rather than propagating an error.
 */
@Component
public class BookingStatusClientFallback implements FallbackFactory<BookingStatusClient> {

    @Override
    public BookingStatusClient create(Throwable cause) {
        return legId -> (Booking) null;
    }
}
