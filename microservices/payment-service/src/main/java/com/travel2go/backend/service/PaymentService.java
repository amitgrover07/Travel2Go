package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.model.Refund;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final RefundRepository refundRepository;
    private final PaymentProvider paymentProvider;
    private final QuoteTokenService quoteTokenService;
    private final PaymentEventPublisher eventPublisher;

    public Payment createOrder(String bookingRef, long amountPaise, String method, String quoteToken, String ownerUserId) {
        Payment existing = findRelevantPayment(bookingRef);
        if (existing != null && ("CREATED".equals(existing.getStatus()) || "CAPTURED".equals(existing.getStatus()))) {
            if (ownerUserId.equals(existing.getOwnerUserId())) {
                log.info("Order already exists for bookingRef {} in status {} - returning existing payment",
                        bookingRef, existing.getStatus());
                return existing;
            }
            throw new PaymentConflictException("Payment for bookingRef " + bookingRef + " is already claimed by another user");
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
            return paymentRepository.save(rejected);
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

        return paymentRepository.save(payment);
    }

    @Transactional
    public void applyWebhook(byte[] rawBody, Map<String, String> headers) {
        WebhookEvent event = paymentProvider.verifyAndParse(rawBody, headers);

        if (event.getType() == WebhookEventType.OTHER) {
            return;
        }

        if (event.getProviderOrderId() == null) {
            log.warn("Webhook for unknown provider order {}", event.getProviderOrderId());
            return;
        }

        if (event.getType() == WebhookEventType.CAPTURED) {
            applyCaptured(event);
        } else {
            applyFailed(event);
        }
    }

    private void applyCaptured(WebhookEvent event) {
        String dedupeKey = event.getProviderPaymentId();
        if (dedupeKey != null) {
            int inserted = processedWebhookEventRepository.recordIfNew(dedupeKey);
            if (inserted == 0) {
                log.info("Webhook for payment {} already processed, skipping", dedupeKey);
                return;
            }
        }

        Payment payment = findPaymentOrLogUnknown(event.getProviderOrderId());
        if (payment == null) {
            return;
        }

        if (payment.getAmountPaise() != event.getAmountPaise()) {
            log.error("Amount mismatch for order {}: expected {} got {} - not capturing",
                    event.getProviderOrderId(), payment.getAmountPaise(), event.getAmountPaise());
            return;
        }

        int updated = paymentRepository.markCaptured(payment.getId(), event.getProviderPaymentId());
        if (updated == 0) {
            log.info("Ignoring webhook for order {} - payment already left CREATED status",
                    event.getProviderOrderId());
            return;
        }

        PaymentCapturedEvent toPublish = new PaymentCapturedEvent(
                payment.getBookingRef(), event.getProviderPaymentId(), payment.getAmountPaise());
        Runnable publishCaptured = () -> {
            try {
                eventPublisher.publish("payment.captured", toPublish);
            } catch (Exception e) {
                log.error("Failed to publish payment.captured event for bookingRef {} providerPaymentId {}: {}",
                        toPublish.bookingRef(), toPublish.providerPaymentId(), e.getMessage(), e);
            }
        };
        // Guard against running outside an active transaction (e.g. plain unit
        // tests instantiating this service directly, bypassing the
        // @Transactional proxy): registerSynchronization() throws
        // IllegalStateException when no synchronization is active, so fall
        // back to publishing immediately in that case.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishCaptured.run();
                }
            });
        } else {
            log.warn("applyCaptured running without an active transaction synchronization - "
                    + "payment.captured will be published immediately instead of deferred to commit; "
                    + "this should only happen in tests, not production");
            publishCaptured.run();
        }
    }

    private void applyFailed(WebhookEvent event) {
        Payment payment = findPaymentOrLogUnknown(event.getProviderOrderId());
        if (payment == null) {
            return;
        }
        if (!"CREATED".equals(payment.getStatus())) {
            log.info("Ignoring webhook for order {} - payment already in terminal status {}",
                    event.getProviderOrderId(), payment.getStatus());
            return;
        }
        payment.setStatus("FAILED");
        paymentRepository.save(payment);
    }

    private Payment findPaymentOrLogUnknown(String providerOrderId) {
        Payment payment = paymentRepository.findByProviderRef(providerOrderId).orElse(null);
        if (payment == null) {
            log.warn("Webhook for unknown provider order {}", providerOrderId);
        }
        return payment;
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

    @Transactional
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
        Payment saved = paymentRepository.save(payment);

        refundRepository.save(Refund.builder()
                .paymentId(saved.getId())
                .providerRefundId(result.getProviderRefundId())
                .amountPaise(saved.getAmountPaise())
                .status("SUCCESS")
                .createdAt(new Date())
                .build());

        Runnable publishRefunded = () -> {
            try {
                eventPublisher.publish("payment.refunded",
                        new PaymentRefundedEvent(bookingRef, result.getProviderRefundId(), saved.getAmountPaise()));
            } catch (Exception e) {
                log.error("Failed to publish payment.refunded event for bookingRef {} providerRefundId {}: {}",
                        bookingRef, result.getProviderRefundId(), e.getMessage(), e);
            }
        };
        // Guard against running outside an active transaction (e.g. plain unit
        // tests instantiating this service directly, bypassing the
        // @Transactional proxy): registerSynchronization() throws
        // IllegalStateException when no synchronization is active, so fall
        // back to publishing immediately in that case.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishRefunded.run();
                }
            });
        } else {
            log.warn("refund running without an active transaction synchronization - "
                    + "payment.refunded will be published immediately instead of deferred to commit; "
                    + "this should only happen in tests, not production");
            publishRefunded.run();
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
        List<Payment> payments = paymentRepository.findByBookingRef(bookingRef);
        if (payments.isEmpty()) {
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
