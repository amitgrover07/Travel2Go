package com.travel2go.backend.consumer;

import com.travel2go.backend.model.Leg;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.service.LegConfirmedEvent;
import com.travel2go.backend.service.TripEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment-service's payment.captured event (P1.1) and flips the
 * matching Leg from PENDING to CONFIRMED, then publishes leg.confirmed
 * (P1.4) so booking-service can confirm its own Booking only once the Leg
 * genuinely is confirmed - single-owner choreography, replacing the old
 * "both services independently listen to payment.captured" arrangement.
 * Idempotent via a status-guard, same pattern as before.
 *
 * The leg.confirmed publish is best-effort: trip-service has no
 * transactional outbox (unlike payment-service, P1.3/P1.4) since it has no
 * relational datastore. A crash between the Leg write and this publish is a
 * known, documented gap - the Leg itself is still correctly CONFIRMED; only
 * the downstream Booking confirmation is at risk in that narrow window.
 *
 * An event for a legId with no matching Leg, or any other unexpected
 * exception, now propagates (P1.4) instead of being swallowed - this
 * queue's RabbitMQConfig gives it a dead-letter exchange + bounded retry,
 * so an unrecoverable message becomes an alertable DLQ entry instead of a
 * silent drop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCapturedConsumer {

    private final LegRepository legRepository;
    private final TripEventPublisher tripEventPublisher;

    @RabbitListener(queues = "trip.payment-captured")
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        Leg leg = legRepository.findById(event.bookingRef()).block();

        if (leg == null) {
            log.error("payment.captured for unknown legId {} (providerPaymentId {}) - no matching Leg found",
                    event.bookingRef(), event.providerPaymentId());
            throw new IllegalStateException("No matching Leg found for legId " + event.bookingRef());
        }

        if (!"PENDING".equals(leg.getStatus())) {
            log.info("Ignoring payment.captured for legId {} - leg already in status {}",
                    event.bookingRef(), leg.getStatus());
            return;
        }

        Long pricePaise = leg.getPricePaise();
        if (pricePaise != null && pricePaise != event.amountPaise()) {
            log.error("Amount mismatch for legId {}: expected {} got {} - not confirming",
                    event.bookingRef(), pricePaise, event.amountPaise());
            return;
        }

        leg.setStatus("CONFIRMED");
        legRepository.save(leg).block();

        try {
            tripEventPublisher.publish("leg.confirmed",
                    new LegConfirmedEvent(event.bookingRef(), event.providerPaymentId(), event.amountPaise()));
        } catch (Exception e) {
            log.error("Failed to publish leg.confirmed for legId {} (providerPaymentId {}): {}",
                    event.bookingRef(), event.providerPaymentId(), e.getMessage(), e);
        }
    }
}
