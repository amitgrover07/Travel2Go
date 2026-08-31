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

    public Payment createOrder(String bookingRef, long amountPaise, String method, String quoteToken) {
        boolean quoteValid = quoteTokenService.isValid(quoteToken, bookingRef, amountPaise);

        if (!quoteValid) {
            Payment rejected = Payment.builder()
                    .bookingRef(bookingRef)
                    .method(method)
                    .status("REJECTED")
                    .amountPaise(amountPaise)
                    .feePaise(0L)
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

        String dedupeKey = event.getProviderPaymentId();
        if (dedupeKey != null && processedWebhookEventRepository.findById(dedupeKey).block() != null) {
            log.info("Webhook for payment {} already processed, skipping", dedupeKey);
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

            eventPublisher.publish("payment.captured",
                    new PaymentCapturedEvent(payment.getBookingRef(), payment.getProviderPaymentId(), payment.getAmountPaise()));
        } else {
            payment.setStatus("FAILED");
            paymentRepository.save(payment).block();
        }
    }

    public Payment getStatus(String bookingRef) {
        return paymentRepository.findByBookingRef(bookingRef)
                .collectList()
                .map(list -> list.stream()
                        .max(Comparator.comparing(Payment::getCreatedAt))
                        .orElseThrow(() -> new IllegalArgumentException("No payment found for bookingRef " + bookingRef)))
                .block();
    }

    public Payment refund(String bookingRef) {
        Payment payment = getStatus(bookingRef);

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

        eventPublisher.publish("payment.refunded",
                new PaymentRefundedEvent(bookingRef, result.getProviderRefundId(), payment.getAmountPaise()));

        return saved;
    }
}
