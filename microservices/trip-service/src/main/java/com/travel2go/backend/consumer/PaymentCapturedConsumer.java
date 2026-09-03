package com.travel2go.backend.consumer;

import com.travel2go.backend.model.Leg;
import com.travel2go.backend.repository.LegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment-service's payment.captured event (P1.1) and flips the
 * matching Leg from PENDING to CONFIRMED. Independent of booking-service's
 * own consumer (both subscribe to the same fan-out event separately) - this
 * one does not send a notification (booking-service's consumer used to;
 * leg-booking confirmation notifications aren't implemented for either
 * consumer yet - see booking-service's PaymentCapturedConsumer). Idempotent
 * via a status-guard, same pattern as booking-service's consumer and
 * payment-service's own (P1.1).
 *
 * An event for a legId with no matching Leg (shouldn't happen) is logged at
 * ERROR and acked, not requeued - see booking-service's PaymentCapturedConsumer
 * for the same reasoning.
 *
 * The whole body runs under a top-level try/catch, same as booking-service's
 * consumer: this queue has no dead-letter exchange or bounded retry policy
 * configured (out of scope), so an uncaught exception would otherwise be
 * requeued by Spring AMQP's default behavior and loop forever on a single
 * poison message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCapturedConsumer {

    private final LegRepository legRepository;

    @RabbitListener(queues = "trip.payment-captured")
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        try {
            Leg leg = legRepository.findById(event.bookingRef()).block();

            if (leg == null) {
                log.error("payment.captured for unknown legId {} (providerPaymentId {}) - no matching Leg found",
                        event.bookingRef(), event.providerPaymentId());
                return;
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
        } catch (Exception e) {
            log.error("Unexpected error processing payment.captured for bookingRef {} (providerPaymentId {}): {}",
                    event.bookingRef(), event.providerPaymentId(), e.getMessage(), e);
        }
    }
}
