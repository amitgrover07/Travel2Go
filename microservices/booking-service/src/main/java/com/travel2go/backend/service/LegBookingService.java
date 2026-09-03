package com.travel2go.backend.service;

import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LegBookingService {

    private final BookingRepository bookingRepository;
    private final QuoteTokenService quoteTokenService;

    public Booking createLegBooking(String tripId, String legId, String quoteToken, Long amountPaise, String ownerUserId) {
        Booking existing = findRelevantBooking(legId);
        if (existing != null && isActive(existing)) {
            if (ownerUserId.equals(existing.getOwnerUserId())) {
                return existing;
            }
            throw new LegBookingConflictException("Booking for legId " + legId + " is already claimed by another user");
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
        Booking booking = findRelevantBooking(legId);
        if (booking == null) {
            throw new IllegalArgumentException("No booking found for legId " + legId);
        }
        if (!isAdmin && !requestingUserId.equals(booking.getOwnerUserId())) {
            throw new IllegalArgumentException("No booking found for legId " + legId);
        }
        return booking;
    }

    private static boolean isActive(Booking booking) {
        return "PENDING".equals(booking.getStatus()) || "CONFIRMED".equals(booking.getStatus());
    }

    /**
     * Picks the booking that matters for a legId when more than one exists:
     * an active (PENDING/CONFIRMED) booking always wins over a rejected one,
     * regardless of which is newer - so a stray REJECTED record left behind
     * by a failed attempt never hides the fact a valid claim exists (or
     * shadows it after a later valid retry). Among bookings of equal
     * activeness, the newest (by bookingDate, null-safe) wins. Returns null
     * if there is no booking at all for this legId.
     */
    private Booking findRelevantBooking(String legId) {
        List<Booking> bookings = bookingRepository.findByLegId(legId).collectList().block();
        if (bookings == null || bookings.isEmpty()) {
            return null;
        }
        return bookings.stream()
                .max(Comparator
                        .<Booking>comparingInt(b -> isActive(b) ? 1 : 0)
                        .thenComparing(Booking::getBookingDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }
}
