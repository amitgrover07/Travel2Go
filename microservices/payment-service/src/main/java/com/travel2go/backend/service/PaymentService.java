package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEvent;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final PaymentProvider paymentProvider;
    private final QuoteTokenService quoteTokenService;
    private final PaymentEventPublisher eventPublisher;

    public Payment createOrder(String bookingRef, long amountPaise, String method, String quoteToken, String ownerUserId) {
        Payment existing = findRelevantPayment(bookingRef);
        if (existing != null && ("CREATED".equals(existing.getStatus()) || "CAPTURED".equals(existing.getStatus()))) {
            log.info("Order already exists for bookingRef {} in status {} - returning existing payment",
                    bookingRef, existing.getStatus());
            return existing;
        }

        boolean quoteValid = quoteTokenService.isValid(quoteToken, bookingRef, amountPaise);

        if (!quoteValid) {
            Payment rejected = Payment.builder()
                    .bookingRef(bookingRef)
                    .method(method)
                    .status("REJECTED")
                    .amountPaise(amountPaise)
                    .feePaise(0L)
                    .ownerUserId(ownerUserId)
                    .quoteTokenValidated(false)
                    .createdAt(new Date())
                    .build();
            return paymentRepository.save(rejected).block();
        }

        CreatedOrder order = paymentProvider.createOrder(bookingRef, amountPaise, method);

        Payment payment = Payment.builder()
                .bookingRef(bookingRef)
                .method(method)
                .status("CREATED")
                .amountPaise(amountPaise)
                .feePaise(0L)
                .providerRef(order.getProviderOrderId())
                .ownerUserId(ownerUserId)
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();

        return paymentRepository.save(payment).block();
    }

    public void applyWebhook(byte[] rawBody, Map<String, String> headers) {
        WebhookEvent event = paymentProvider.verifyAndParse(rawBody, headers);

        if (event.getType() == WebhookEventType.OTHER) {
            return;
        }

        if (event.getProviderOrderId() == null) {
            log.warn("Webhook for unknown provider order {}", event.getProviderOrderId());
            return;
        }

        String dedupeKey = event.getProviderPaymentId();
        if (dedupeKey != null && processedWebhookEventRepository.findById(dedupeKey).block() != null) {
            log.info("Webhook for payment {} already processed, skipping", dedupeKey);
            return;
        }

        Payment payment = paymentRepository.findByProviderRef(event.getProviderOrderId())
                .next()
                .block();

        if (payment == null) {
            log.warn("Webhook for unknown provider order {}", event.getProviderOrderId());
            return;
        }

        if (!"CREATED".equals(payment.getStatus())) {
            log.info("Ignoring webhook for order {} - payment already in terminal status {}",
                    event.getProviderOrderId(), payment.getStatus());
            return;
        }

        if (event.getType() == WebhookEventType.CAPTURED) {
            if (payment.getAmountPaise() != event.getAmountPaise()) {
                log.error("Amount mismatch for order {}: expected {} got {} - not capturing",
                        event.getProviderOrderId(), payment.getAmountPaise(), event.getAmountPaise());
                return;
            }
            payment.setStatus("CAPTURED");
            payment.setProviderPaymentId(event.getProviderPaymentId());
            paymentRepository.save(payment).block();

            if (dedupeKey != null) {
                processedWebhookEventRepository.save(
                        ProcessedWebhookEvent.builder().id(dedupeKey).processedAt(new Date()).build()).block();
            }

            try {
                eventPublisher.publish("payment.captured",
                        new PaymentCapturedEvent(payment.getBookingRef(), payment.getProviderPaymentId(), payment.getAmountPaise()));
            } catch (Exception e) {
                log.error("Failed to publish payment.captured event for bookingRef {} providerPaymentId {}: {}",
                        payment.getBookingRef(), payment.getProviderPaymentId(), e.getMessage(), e);
            }
        } else {
            payment.setStatus("FAILED");
            paymentRepository.save(payment).block();
        }
    }

    public Payment getStatus(String bookingRef, String requestingUserId, boolean isAdmin) {
        Payment payment = findRelevantPayment(bookingRef);
        if (payment == null) {
            throw new IllegalArgumentException("No payment found for bookingRef " + bookingRef);
        }
        if (!isAdmin && !requestingUserId.equals(payment.getOwnerUserId())) {
            throw new IllegalArgumentException("No payment found for bookingRef " + bookingRef);
        }
        return payment;
    }

    public Payment refund(String bookingRef) {
        Payment payment = getStatus(bookingRef, bookingRef, true);

        if ("REFUNDED".equals(payment.getStatus())) {
            return payment;
        }
        if (!"CAPTURED".equals(payment.getStatus())) {
            throw new IllegalStateException("Cannot refund payment in status " + payment.getStatus());
        }

        RefundResult result = paymentProvider.refund(payment.getProviderPaymentId(), payment.getAmountPaise());
        if (!"SUCCESS".equals(result.getStatus())) {
            throw new IllegalStateException("Refund failed for bookingRef " + bookingRef);
        }

        payment.setStatus("REFUNDED");
        Payment saved = paymentRepository.save(payment).block();

        try {
            eventPublisher.publish("payment.refunded",
                    new PaymentRefundedEvent(bookingRef, result.getProviderRefundId(), payment.getAmountPaise()));
        } catch (Exception e) {
            log.error("Failed to publish payment.refunded event for bookingRef {} providerRefundId {}: {}",
                    bookingRef, result.getProviderRefundId(), e.getMessage(), e);
        }

        return saved;
    }

    /**
     * Picks the payment that matters for a bookingRef when more than one
     * exists: a settled (CAPTURED/REFUNDED) payment always wins over an
     * unsettled (CREATED/REJECTED/FAILED) one, regardless of which is newer -
     * so a stray/duplicate order created after a capture never hides the
     * fact the booking is already paid for. Among payments of equal
     * settledness, the newest (by createdAt, null-safe) wins. Returns null
     * if there is no payment at all for this bookingRef.
     */
    private Payment findRelevantPayment(String bookingRef) {
        List<Payment> payments = paymentRepository.findByBookingRef(bookingRef).collectList().block();
        if (payments == null || payments.isEmpty()) {
            return null;
        }
        return payments.stream()
                .max(Comparator
                        .<Payment>comparingInt(p -> isSettled(p) ? 1 : 0)
                        .thenComparing(Payment::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static boolean isSettled(Payment payment) {
        return "CAPTURED".equals(payment.getStatus()) || "REFUNDED".equals(payment.getStatus());
    }
}
