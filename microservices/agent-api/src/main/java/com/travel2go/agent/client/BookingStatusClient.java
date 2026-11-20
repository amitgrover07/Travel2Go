package com.travel2go.agent.client;

import com.travel2go.backend.model.Booking;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Reads leg-booking status from booking-service. Circuit breaker + timeout via the
 * shared Resilience4j config; a failure degrades through {@link BookingStatusClientFallback}.
 *
 * PRD-OPEN: service-to-service auth. booking-service's endpoint is user-JWT gated;
 * a dedicated internal/service credential (not an end-user token) needs to be wired
 * here before the live hop works end to end. The contract and resilience are in place;
 * only the credential wiring is deferred.
 */
@FeignClient(
        name = "booking-service",
        url = "${agent.clients.booking-url}",
        fallbackFactory = BookingStatusClientFallback.class)
public interface BookingStatusClient {

    @GetMapping("/api/leg-bookings/{legId}")
    Booking getByLegId(@PathVariable("legId") String legId);
}
