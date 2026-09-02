package com.travel2go.backend.service;

import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LegBookingService {

    private final BookingRepository bookingRepository;
    private final QuoteTokenService quoteTokenService;

    public Booking createLegBooking(String tripId, String legId, String quoteToken, Long amountPaise, String ownerUserId) {
        Booking existing = findByLegId(legId);
        if (existing != null) {
            return existing;
        }

        boolean quoteValid = quoteTokenService.isValid(quoteToken, legId, amountPaise);

        if (!quoteValid) {
            Booking rejected = Booking.builder()
                    .tripId(tripId)
                    .legId(legId)
                    .quoteToken(quoteToken)
                    .amountPaise(amountPaise)
                    .feePaise(0L)
                    .ownerUserId(ownerUserId)
                    .status("REJECTED")
                    .bookingDate(new Date())
                    .build();
            bookingRepository.save(rejected).block();
            throw new LegBookingRejectedException("Invalid quote token for legId " + legId);
        }

        Booking booking = Booking.builder()
                .tripId(tripId)
                .legId(legId)
                .quoteToken(quoteToken)
                .amountPaise(amountPaise)
                .feePaise(0L)
                .ownerUserId(ownerUserId)
                .status("PENDING")
                .bookingDate(new Date())
                .build();

        return bookingRepository.save(booking).block();
    }

    public Booking getBooking(String legId, String requestingUserId, boolean isAdmin) {
        Booking booking = findByLegId(legId);
        if (booking == null) {
            throw new IllegalArgumentException("No booking found for legId " + legId);
        }
        if (!isAdmin && !requestingUserId.equals(booking.getOwnerUserId())) {
            throw new IllegalArgumentException("No booking found for legId " + legId);
        }
        return booking;
    }

    private Booking findByLegId(String legId) {
        List<Booking> bookings = bookingRepository.findByLegId(legId).collectList().block();
        if (bookings == null || bookings.isEmpty()) {
            return null;
        }
        return bookings.get(0);
    }
}
