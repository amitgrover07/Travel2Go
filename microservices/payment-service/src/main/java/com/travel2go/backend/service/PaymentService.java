package com.travel2go.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.OutboxEntry;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.model.Refund;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.OutboxRepository;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** Headline trust promise: refunds target settlement within 72 hours. */
    private static final long SLA_HOURS = 72;

    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final RefundRepository refundRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentProvider paymentProvider;
    private final QuoteTokenService quoteTokenService;
    private final ObjectMapper objectMapper;
    private final RefundFloatService refundFloatService;

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
        writeOutboxEntry(payment.getBookingRef(), "payment.captured", toPublish);
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

    /** Back-compatible manual refund (admin-triggered). */
    public Payment refund(String bookingRef) {
        return refund(bookingRef, "MANUAL");
    }

    /**
     * Guaranteed, tracked, SLA-bound refund (F3). Exactly-once per payment under
     * concurrency: the refund slot is claimed with an atomic
     * INSERT ... ON CONFLICT (payment_id) DO NOTHING, so only one caller ever runs
     * the provider refund, decrements the float, flips the payment to REFUNDED and
     * emits payment.refunded. The SLA countdown (now + 72h) starts at initiation and
     * is tracked on the refund row until it settles or breaches.
     *
     * <p>The refund amount is always re-derived from the ledger (the captured
     * payment amount), never from client input, and no fee is ever added (G1).
     */
    @Transactional
    public Payment refund(String bookingRef, String trigger) {
        Payment payment = getStatus(bookingRef, bookingRef, true);

        if ("REFUNDED".equals(payment.getStatus())) {
            return payment;
        }
        if (!"CAPTURED".equals(payment.getStatus())) {
            throw new IllegalStateException("Cannot refund payment in status " + payment.getStatus());
        }

        UUID refundId = UUID.randomUUID();
        Date slaDueAt = new Date(System.currentTimeMillis() + SLA_HOURS * 3600_000L);
        int claimed = refundRepository.insertInitiatedIfNew(
                refundId, payment.getId(), payment.getAmountPaise(), slaDueAt, normaliseTrigger(trigger));
        if (claimed == 0) {
            // Another concurrent or earlier request already owns this payment's refund.
            log.info("Refund for bookingRef {} already claimed - skipping duplicate", bookingRef);
            return findRelevantPayment(bookingRef);
        }

        RefundResult result = paymentProvider.refund(payment.getProviderPaymentId(), payment.getAmountPaise());
        if (!"SUCCESS".equals(result.getStatus())) {
            // Roll back the whole transaction (including the claim) so a failed refund is safe to retry.
            throw new IllegalStateException("Refund failed for bookingRef " + bookingRef);
        }

        // Debit the ring-fenced float and complete the refund row (SLA stays INITIATED until settled).
        UUID floatTxnId = refundFloatService.debit(payment.getAmountPaise(), refundId);
        refundRepository.completeInitiated(refundId, result.getProviderRefundId(), floatTxnId);

        payment.setStatus("REFUNDED");
        Payment saved = paymentRepository.save(payment);

        PaymentRefundedEvent toPublish = new PaymentRefundedEvent(bookingRef, result.getProviderRefundId(), saved.getAmountPaise());
        writeOutboxEntry(bookingRef, "payment.refunded", toPublish);

        return saved;
    }

    /** Owner-checked lookup of the refund for a booking (for the status endpoint). */
    public java.util.Optional<Refund> getRefund(String bookingRef, String requestingUserId, boolean isAdmin) {
        Payment payment = getStatus(bookingRef, requestingUserId, isAdmin);
        return refundRepository.findFirstByPaymentId(payment.getId());
    }

    /** Marks a refund settled (INITIATED -> SETTLED), stopping its SLA countdown. Idempotent. */
    @Transactional
    public void markRefundSettled(String bookingRef) {
        Payment payment = getStatus(bookingRef, bookingRef, true);
        refundRepository.markSettledIfInitiated(payment.getId());
    }

    /**
     * Flags any refund that has blown its 72h SLA: INITIATED -> BREACHED (guarded, so
     * each breach transitions once) and emits payment.refund_sla_breached via the outbox.
     * Driven by {@link RefundSlaMonitor}.
     */
    @Transactional
    public void flagSlaBreaches() {
        List<Refund> overdue = refundRepository.findBySlaStatusAndSlaDueAtBefore("INITIATED", new Date());
        for (Refund refund : overdue) {
            if (refundRepository.markBreachedIfInitiated(refund.getId()) == 0) {
                continue; // someone else flagged it first
            }
            Payment payment = paymentRepository.findById(refund.getPaymentId()).orElse(null);
            String bookingRef = payment != null ? payment.getBookingRef() : null;
            writeOutboxEntry(bookingRef == null ? refund.getId().toString() : bookingRef,
                    "payment.refund_sla_breached",
                    new RefundSlaBreachedEvent(bookingRef, refund.getId(), refund.getSlaDueAt()));
        }
    }

    private String normaliseTrigger(String trigger) {
        if (trigger == null) {
            return "MANUAL";
        }
        String t = trigger.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (t) {
            case "USER_CANCEL", "DISRUPTION", "MANUAL" -> t;
            default -> "MANUAL";
        };
    }

    private void writeOutboxEntry(String aggregateId, String type, Object payload) {
        outboxRepository.save(OutboxEntry.builder()
                .aggregateId(aggregateId)
                .type(type)
                .payload(toJson(payload))
                .createdAt(new Date())
                .attempts(0)
                .build());
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
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
