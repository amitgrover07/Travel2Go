package com.travel2go.backend.controller;

import com.travel2go.backend.dto.AddLegRequest;
import com.travel2go.backend.dto.BookLegRequest;
import com.travel2go.backend.dto.CreateTripRequest;
import com.travel2go.backend.dto.TripDetailResponse;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import com.travel2go.backend.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping
    public ResponseEntity<Trip> createTrip(@RequestBody CreateTripRequest request) {
        return ResponseEntity.ok(tripService.createTrip(request, currentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDetailResponse> getTrip(@PathVariable String id) {
        return ResponseEntity.ok(tripService.getTripDetail(id, currentUserId()));
    }

    @PostMapping("/{id}/legs")
    public ResponseEntity<Leg> addLeg(@PathVariable String id, @RequestBody AddLegRequest request) {
        return ResponseEntity.ok(tripService.addLeg(id, request, currentUserId()));
    }

    @PostMapping("/{id}/legs/{legId}/book")
    public ResponseEntity<Leg> bookLeg(@PathVariable String id, @PathVariable String legId,
            @RequestBody(required = false) BookLegRequest request) {
        String email = request != null ? request.getEmail() : null;
        String phone = request != null ? request.getPhone() : null;
        return ResponseEntity.ok(tripService.bookLeg(id, legId, currentUserId(), email, phone));
    }

    /**
     * Server-priced quote for a leg (F2 agent quoting). trip-service is the price
     * authority: it signs the leg's real price, so a caller can never choose the
     * price sealed into the token (G2).
     */
    @GetMapping("/legs/{legId}/quote")
    public ResponseEntity<com.travel2go.backend.dto.LegQuoteResponse> quoteLeg(@PathVariable String legId) {
        try {
            return ResponseEntity.ok(tripService.quoteLeg(legId, currentUserId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
