package com.travel2go.backend.consumer;

import com.travel2go.backend.client.NotificationClient;
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
 * matching Booking, then sends a real confirmation notification (MVP 1C -
 * replaces the earlier "not yet implemented" log line). Idempotent via the
 * same status-guard as before: a redelivered leg.confirmed finds the
 * Booking already CONFIRMED and returns before ever reaching either the
 * save or the notification call, so no new dedupe state is needed.
 *
 * A notification-service outage does not affect the Booking - B5's circuit
 * breaker + fallback factory already absorbs that failure by logging and
 * swallowing, matching the money-path guarantee that a notification never
 * rolls back a confirmation.
 *
 * An event for a legId with no matching Booking, or any other unexpected
 * exception, propagates (P1.4) - this queue's RabbitMQConfig gives it a
 * dead-letter exchange + bounded retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegConfirmedConsumer {

    private final BookingRepository bookingRepository;
    private final NotificationClient notificationClient;

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

        notificationClient.sendLegBookingConfirmation(new NotificationClient.LegBookingConfirmationRequest(
                booking.getEmail(), booking.getLegId(), booking.getAmountPaise(), booking.getId()));

        log.info("Booking {} confirmed (legId {}) and confirmation notification sent",
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
