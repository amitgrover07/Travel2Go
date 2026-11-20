package com.travel2go.agent.client;

import com.travel2go.backend.model.Booking;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Reads leg-booking status from booking-service. Circuit breaker + timeout via the
 * shared Resilience4j config; a failure degrades through {@link BookingStatusClientFallback}.
 *
 * <p>The authenticated agent id is forwarded as {@code X-Agent-Id} so the lookup can
 * be scoped to bookings that agent is authorized for, rather than any bookingRef
 * (guards against IDOR).
 *
 * PRD-OPEN: service-to-service auth + the ownership enforcement itself. booking-service's
 * endpoint is user-JWT gated today; a dedicated internal/service credential and the
 * agent-&gt;traveller authorization model must be wired before the live hop both works
 * and enforces scope. The contract, resilience, and identity propagation are in place.
 */
@FeignClient(
        name = "booking-service",
        url = "${agent.clients.booking-url}",
        fallbackFactory = BookingStatusClientFallback.class)
public interface BookingStatusClient {

    @GetMapping("/api/leg-bookings/{legId}")
    Booking getByLegId(@PathVariable("legId") String legId, @RequestHeader("X-Agent-Id") String agentId);
}
