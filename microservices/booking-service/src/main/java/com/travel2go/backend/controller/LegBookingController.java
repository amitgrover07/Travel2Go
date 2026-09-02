package com.travel2go.backend.controller;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.service.LegBookingConflictException;
import com.travel2go.backend.service.LegBookingRejectedException;
import com.travel2go.backend.service.LegBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leg-bookings")
@RequiredArgsConstructor
public class LegBookingController {

    private final LegBookingService legBookingService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping
    public ResponseEntity<LegBookingResponse> createLegBooking(@RequestBody LegBookingRequest request) {
        try {
            Booking booking = legBookingService.createLegBooking(
                    request.getTripId(), request.getLegId(), request.getQuoteToken(),
                    request.getAmountPaise(), currentUserId());
            return ResponseEntity.ok(new LegBookingResponse(booking.getLegId(), booking.getStatus()));
        } catch (LegBookingRejectedException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
        } catch (LegBookingConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/{legId}")
    public ResponseEntity<Booking> getBooking(@PathVariable String legId) {
        try {
            return ResponseEntity.ok(legBookingService.getBooking(legId, currentUserId(), currentUserIsAdmin()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
