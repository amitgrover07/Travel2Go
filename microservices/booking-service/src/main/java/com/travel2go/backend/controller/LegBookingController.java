package com.travel2go.backend.controller;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/api/leg-bookings")
@RequiredArgsConstructor
public class LegBookingController {

    private final BookingRepository bookingRepository;

    @PostMapping
    public ResponseEntity<LegBookingResponse> createLegBooking(@RequestBody LegBookingRequest request) {
        Booking booking = Booking.builder()
                .tripId(request.getTripId())
                .legId(request.getLegId())
                .quoteToken(request.getQuoteToken())
                .amountPaise(request.getAmountPaise())
                .feePaise(0L)
                .status("CONFIRMED")
                .bookingDate(new Date())
                .build();

        Booking saved = bookingRepository.save(booking).block();

        return ResponseEntity.ok(new LegBookingResponse(saved.getId(), saved.getStatus()));
    }
}
