package com.travel2go.backend.consumer;

import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Consumes trip-service's leg.confirmed event (P1.4) and confirms the
 * matching Booking. Replaces the old direct payment.captured consumer -
 * single-owner choreography means a Booking can now only be CONFIRMED once
 * its Leg already is, closing the "two independent confirmations of the
 * same fact can diverge" gap. Idempotent via a status-guard, same pattern
 * as before.
 *
 * An event for a legId with no matching Booking, or any other unexpected
 * exception, now propagates (P1.4) instead of being swallowed - this
 * queue's RabbitMQConfig gives it a dead-letter exchange + bounded retry,
 * so an unrecoverable message becomes an alertable DLQ entry instead of a
 * silent drop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegConfirmedConsumer {

    private final BookingRepository bookingRepository;

    @RabbitListener(queues = "booking.leg-confirmed")
    public void onLegConfirmed(LegConfirmedEvent event) {
        Booking booking = findRelevantBooking(event.legId());

        if (booking == null) {
            log.error("leg.confirmed for unknown legId {} (providerPaymentId {}) - no matching Booking found",
                    event.legId(), event.providerPaymentId());
            throw new IllegalStateException("No matching Booking found for legId " + event.legId());
        }

        if (!"PENDING".equals(booking.getStatus())) {
            log.info("Ignoring leg.confirmed for legId {} - booking already in status {}",
                    event.legId(), booking.getStatus());
            return;
        }

        if (booking.getAmountPaise() != event.amountPaise()) {
            log.error("Amount mismatch for legId {}: expected {} got {} - not confirming",
                    event.legId(), booking.getAmountPaise(), event.amountPaise());
            return;
        }

        booking.setStatus("CONFIRMED");
        booking.setProviderPaymentId(event.providerPaymentId());
        booking.setConfirmedAt(new Date());
        bookingRepository.save(booking).block();

        log.info("Booking {} confirmed (legId {}) - leg-booking confirmation notifications not yet implemented "
                        + "(needs a notification payload shape for leg bookings, tracked separately)",
                booking.getId(), booking.getLegId());
    }

    private static boolean isActive(Booking booking) {
        return "PENDING".equals(booking.getStatus()) || "CONFIRMED".equals(booking.getStatus());
    }

    /**
     * Picks the booking that matters for a legId when more than one exists:
     * an active (PENDING/CONFIRMED) booking always wins over a rejected one,
     * regardless of which is newer. Among bookings of equal activeness, the
     * newest (by bookingDate, null-safe) wins. Returns null if there is no
     * booking at all for this legId.
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
