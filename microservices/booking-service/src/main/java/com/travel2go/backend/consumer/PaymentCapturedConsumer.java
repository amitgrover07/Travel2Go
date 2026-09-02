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
 * Consumes payment-service's payment.captured event (P1.1) and confirms the
 * matching Booking. Idempotent via a status-guard (mirrors the pattern P1.1's
 * final review validated for payment-service's own consumer) - no separate
 * dedupe store.
 *
 * An event for a legId with no Booking yet (an ordering race - the capture
 * arriving before the booking record exists - which shouldn't happen but
 * must not crash) is logged at ERROR (loud, alertable) and acked rather than
 * requeued: without a real delay/backoff mechanism (P1.4/saga territory),
 * blind requeue would just spin on a message that can never resolve itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCapturedConsumer {

    private final BookingRepository bookingRepository;
    private final NotificationClient notificationClient;

    @RabbitListener(queues = "booking.payment-captured")
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        Booking booking = findRelevantBooking(event.bookingRef());

        if (booking == null) {
            log.error("payment.captured for unknown legId {} (providerPaymentId {}) - no matching Booking found",
                    event.bookingRef(), event.providerPaymentId());
            return;
        }

        if (!"PENDING".equals(booking.getStatus())) {
            log.info("Ignoring payment.captured for legId {} - booking already in status {}",
                    event.bookingRef(), booking.getStatus());
            return;
        }

        if (booking.getAmountPaise() != event.amountPaise()) {
            log.error("Amount mismatch for legId {}: expected {} got {} - not confirming",
                    event.bookingRef(), booking.getAmountPaise(), event.amountPaise());
            return;
        }

        booking.setStatus("CONFIRMED");
        booking.setProviderPaymentId(event.providerPaymentId());
        booking.setConfirmedAt(new Date());
        bookingRepository.save(booking).block();

        try {
            notificationClient.sendBookingConfirmation(
                    new NotificationClient.NotificationRequest(null, null, null, booking));
        } catch (Exception e) {
            log.warn("Failed to send booking confirmation notification for legId {}: {}",
                    event.bookingRef(), e.getMessage());
        }
    }

    private static boolean isActive(Booking booking) {
        return "PENDING".equals(booking.getStatus()) || "CONFIRMED".equals(booking.getStatus());
    }

    /**
     * Picks the booking that matters for a legId when more than one exists:
     * an active (PENDING/CONFIRMED) booking always wins over a rejected one,
     * regardless of which is newer - so a stray REJECTED record left behind
     * by a failed attempt (Task 3's fix allows a fresh retry after a
     * REJECTED attempt) never causes a real payment.captured event to
     * silently miss the actual active booking. Among bookings of equal
     * activeness, the newest (by bookingDate, null-safe) wins. Returns null
     * if there is no booking at all for this legId. Local duplicate of
     * LegBookingService's private findRelevantBooking (different package -
     * com.travel2go.backend.consumer vs com.travel2go.backend.service -
     * so it can't be imported/reused directly).
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
