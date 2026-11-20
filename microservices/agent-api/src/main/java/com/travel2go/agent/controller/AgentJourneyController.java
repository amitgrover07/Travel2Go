package com.travel2go.agent.controller;

import com.travel2go.agent.client.BookingStatusClient;
import com.travel2go.agent.dto.AgentJourneyStatusResponse;
import com.travel2go.backend.model.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only journey/booking status for an agent. Composes booking-service; on an
 * upstream failure the circuit-breaker fallback yields available=false rather than
 * surfacing an internal error.
 */
@RestController
@RequestMapping("/api/agent/v1/journeys")
@RequiredArgsConstructor
public class AgentJourneyController {

    private final BookingStatusClient bookingStatusClient;

    @GetMapping("/{bookingRef}/status")
    public ResponseEntity<AgentJourneyStatusResponse> status(@PathVariable String bookingRef) {
        Booking booking = bookingStatusClient.getByLegId(bookingRef);
        if (booking == null) {
            return ResponseEntity.ok(AgentJourneyStatusResponse.builder()
                    .legId(bookingRef)
                    .available(false)
                    .build());
        }
        return ResponseEntity.ok(AgentJourneyStatusResponse.builder()
                .legId(booking.getLegId() != null ? booking.getLegId() : bookingRef)
                .status(booking.getStatus())
                .amountPaise(booking.getAmountPaise())
                .available(true)
                .build());
    }
}
