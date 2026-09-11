# P1.4: Saga + Transactional Outbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the three remaining reliability gaps in the payment→confirmation flow: payment-service's capture/refund publish becomes durable (transactional outbox + relay, replacing the P1.3 `afterCommit()` direct-publish), booking-service and trip-service gain a DLQ + bounded retry on their `payment.captured` consumers, and confirmation ownership moves to single-owner choreography (trip confirms `Leg` and emits `leg.confirmed`; booking re-binds to consume that instead of `payment.captured` directly).

**Architecture:** payment-service writes an `outbox` row in the same `@Transactional` block that updates payment/refund state, and a `@Scheduled` `OutboxRelay` (using `FOR UPDATE SKIP LOCKED`, safe across payment-service's multiple Cloud Run instances) becomes the sole publisher. trip-service's existing `payment.captured` consumer confirms `Leg` then best-effort-publishes a new `leg.confirmed` event. booking-service re-binds from `payment.captured` to `leg.confirmed`. Both consumers gain a dead-letter exchange + bounded retry (4 attempts, 1s→10s backoff) and lose their current top-level catch-and-swallow.

**Tech Stack:** Spring Data JPA (outbox), Spring's `@Scheduled`, Jackson (`ObjectMapper`, already on the classpath via `spring-boot-starter-web`), Spring AMQP dead-letter exchanges + `spring.rabbitmq.listener.simple.retry`, Testcontainers (`postgresql` for payment-service, new `rabbitmq` module for booking-service/trip-service).

## Global Constraints

- Event payload shapes stay byte-identical: `PaymentCapturedEvent(String bookingRef, String providerPaymentId, long amountPaise)`, `PaymentRefundedEvent(String bookingRef, String providerRefundId, long amountPaise)` — no consumer-visible wire-format change.
- G1 (`fee_paise` always 0) and payment-gated confirmation are untouched by this ticket — no task here modifies money-state logic, only how/when events are published and how failures are handled downstream.
- Retry parameters, exactly: `max-attempts=4`, `initial-interval=1s` (1000ms), `multiplier=2`, `max-interval=10s` (10000ms), `default-requeue-rejected=false`.
- The RabbitMQ `trustedPackages` hardening (scoped to each service's own `com.travel2go.backend.consumer` package, `TypePrecedence.INFERRED`) must survive unchanged in booking-service and trip-service's `RabbitMQConfig` — no task widens it.
- Compensation for an unrecoverable post-capture failure is the existing `POST /api/payments/{bookingRef}/refund` endpoint (P1.1), human-triggered only — no new compensation code in this plan.
- trip-service's `leg.confirmed` publish is best-effort (no outbox) — trip-service has no Postgres/Flyway/JPA and this plan does not add any (see spec Decision 1).

---

### Task 1: Payment-service outbox schema, entity, and `PaymentService` rewrite

Replaces `PaymentService.applyCaptured`/`refund`'s `afterCommit()` direct-publish with a durable outbox row written in the same transaction. Does not yet add the relay (Task 2) — after this task, outbox rows are written but nothing publishes them yet; that's fine, this task's own tests only prove the write side.

**Files:**
- Create: `microservices/payment-service/src/main/resources/db/migration/V2__outbox.sql`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/model/OutboxEntry.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/OutboxRepository.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`

**Interfaces:**
- Produces: `OutboxEntry` (JPA `@Entity`, table `outbox`): `id` (UUID, generated), `aggregateId` (String), `type` (String), `payload` (String, mapped to a `jsonb` column via `@JdbcTypeCode(SqlTypes.JSON)`), `createdAt`/`publishedAt` (Date), `attempts` (Integer, default 0).
- Produces: `OutboxRepository extends JpaRepository<OutboxEntry, UUID>` with `List<OutboxEntry> findBatchForPublishing(int limit)` — a native `SELECT ... FOR UPDATE SKIP LOCKED` query.
- Consumed by Task 2: `OutboxEntry`'s exact field names/types above, `OutboxRepository.findBatchForPublishing`.
- Consumes: `PaymentCapturedEvent`, `PaymentRefundedEvent` (unchanged, already exist in `com.travel2go.backend.service`).
- `PaymentService`'s constructor field order after this task: `paymentRepository, processedWebhookEventRepository, refundRepository, outboxRepository, paymentProvider, quoteTokenService, objectMapper` — `PaymentEventPublisher` is **removed** from `PaymentService`'s dependencies entirely (it moves to being used only by Task 2's `OutboxRelay`).

- [ ] **Step 1: Write the Flyway migration**

```sql
-- microservices/payment-service/src/main/resources/db/migration/V2__outbox.sql
CREATE TABLE outbox (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id TEXT NOT NULL,
    type         TEXT NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
```

- [ ] **Step 2: Create the `OutboxEntry` entity**

```java
package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.UUID;

/**
 * Transactional outbox row: written in the same DB transaction as the money
 * state change it describes, so a crash after commit can never lose the
 * event - the relay (see OutboxRelay) is the only thing that ever reads
 * "published_at IS NULL" rows and publishes them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "outbox")
public class OutboxEntry {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "published_at")
    private Date publishedAt;

    @Builder.Default
    private Integer attempts = 0;
}
```

- [ ] **Step 3: Create `OutboxRepository`**

```java
package com.travel2go.backend.repository;

import com.travel2go.backend.model.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
    /**
     * SKIP LOCKED is required, not optional: payment-service runs up to 3
     * Cloud Run instances, so more than one relay poller can be ticking
     * concurrently against this table. A row already locked by another
     * instance's in-flight publish is simply excluded from this batch
     * rather than blocking - it will be picked up by whichever instance
     * finishes first, on its next tick.
     */
    @Query(value = "SELECT * FROM outbox WHERE published_at IS NULL "
            + "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEntry> findBatchForPublishing(@Param("limit") int limit);
}
```

- [ ] **Step 4: Rewrite `PaymentService`**

Replace the full content of `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`:

```java
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final RefundRepository refundRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentProvider paymentProvider;
    private final QuoteTokenService quoteTokenService;
    private final ObjectMapper objectMapper;

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

        PaymentRefundedEvent toPublish = new PaymentRefundedEvent(bookingRef, result.getProviderRefundId(), saved.getAmountPaise());
        writeOutboxEntry(bookingRef, "payment.refunded", toPublish);

        return saved;
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
```

- [ ] **Step 5: Rewrite `PaymentServiceTest`**

Replace the full content of `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`:

```java
package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.OutboxEntry;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.OutboxRepository;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, processedWebhookEventRepository, refundRepository, outboxRepository,
                paymentProvider, quoteTokenService, objectMapper);
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(refundRepository.save(any(com.travel2go.backend.model.Refund.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxRepository.save(any(OutboxEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(processedWebhookEventRepository.findById(any(String.class))).thenReturn(Optional.empty());
        lenient().when(paymentRepository.findByBookingRef(any())).thenReturn(List.of());
    }

    private Payment createdPayment() {
        return Payment.builder()
                .bookingRef("leg-1")
                .method("UPI")
                .status("CREATED")
                .amountPaise(150000L)
                .feePaise(0L)
                .providerRef("order_1")
                .ownerUserId("user-1")
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();
    }

    private boolean outboxEntryMatches(OutboxEntry entry, String expectedType, Object expectedEvent) {
        if (!expectedType.equals(entry.getType())) {
            return false;
        }
        try {
            Object actual = objectMapper.readValue(entry.getPayload(), expectedEvent.getClass());
            return expectedEvent.equals(actual);
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void createOrder_succeedsAndNeverAddsAFee() {
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_1", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1");

        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        assertThat(result.getProviderRef()).isEqualTo("order_1");
    }

    @Test
    void createOrder_rejectsWhenQuoteTokenInvalid_withoutCallingProvider() {
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "bad-token", "user-1");

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
    }

    @Test
    void createOrder_returnsExistingPaymentWhenAlreadyCreatedForBookingRef() {
        Payment existing = createdPayment();
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(existing));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-1");

        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo("CREATED");
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
    }

    @Test
    void createOrder_returnsExistingPaymentWhenAlreadyCapturedForBookingRef() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(captured));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-1");

        assertThat(result).isSameAs(captured);
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
    }

    @Test
    void createOrder_differentOwnerCreatedThrowsConflictAndDoesNotPersist() {
        Payment existing = createdPayment();
        existing.setOwnerUserId("user-1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(existing));

        assertThatThrownBy(() ->
                paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-2"))
                .isInstanceOf(PaymentConflictException.class);

        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrder_differentOwnerCapturedThrowsConflictAndDoesNotPersist() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        captured.setOwnerUserId("user-1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(captured));

        assertThatThrownBy(() ->
                paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-2"))
                .isInstanceOf(PaymentConflictException.class);

        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrder_ignoresRejectedPaymentAndCreatesNewOrder() {
        Payment rejected = createdPayment();
        rejected.setStatus("REJECTED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(rejected));
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_2", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1");

        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getProviderRef()).isEqualTo("order_2");
        verify(paymentProvider).createOrder("leg-1", 150000L, "UPI");
    }

    @Test
    void applyWebhook_capturedTransitionsPaymentAndWritesOutboxEntry() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(1);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository).markCaptured(payment.getId(), "pay_1");
        PaymentCapturedEvent expected = new PaymentCapturedEvent("leg-1", "pay_1", 150000L);
        verify(outboxRepository).save(argThat(entry ->
                outboxEntryMatches(entry, "payment.captured", expected)
                        && "leg-1".equals(entry.getAggregateId())));
    }

    @Test
    void applyWebhook_duplicateCapturedIsIdempotent_writesOutboxEntryOnlyOnce() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1, 0);
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(1);

        paymentService.applyWebhook("{}".getBytes(), Map.of());
        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(outboxRepository, times(1)).save(any(OutboxEntry.class));
    }

    @Test
    void applyWebhook_amountMismatchDoesNotCapture() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(createdPayment()));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 999L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).markCaptured(any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void applyWebhook_duplicateEventIdIsNoOpEvenIfPaymentStillCreated() {
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(0);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).findByProviderRef(any());
        verify(paymentRepository, never()).markCaptured(any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void applyWebhook_captureRaceLoserIsNoOp_whenMarkCapturedAffectsZeroRows() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(0);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void applyWebhook_failedAfterCapturedIsIgnored() {
        Payment alreadyCaptured = createdPayment();
        alreadyCaptured.setStatus("CAPTURED");
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(alreadyCaptured));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.FAILED, "order_1", "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void applyWebhook_nullProviderOrderIdIsIgnored() {
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, null, "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).findByProviderRef(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void applyWebhook_unknownOrderIsIgnored() {
        when(paymentRepository.findByProviderRef("order_unknown")).thenReturn(Optional.empty());
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_unknown", "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refund_transitionsCapturedToRefunded() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(captured));
        when(paymentProvider.refund("pay_1", 150000L)).thenReturn(new RefundResult("SUCCESS", "rfnd_1"));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        PaymentRefundedEvent expected = new PaymentRefundedEvent("leg-1", "rfnd_1", 150000L);
        verify(outboxRepository).save(argThat(entry -> outboxEntryMatches(entry, "payment.refunded", expected)));
    }

    @Test
    void refund_isIdempotentOnAlreadyRefunded() {
        Payment refunded = createdPayment();
        refunded.setStatus("REFUNDED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(refunded));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(paymentProvider, never()).refund(any(), anyLong());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void getStatus_prefersCapturedPaymentOverNewerRejectedPayment() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        captured.setCreatedAt(new Date(1000L));

        Payment newerRejected = createdPayment();
        newerRejected.setStatus("REJECTED");
        newerRejected.setCreatedAt(new Date(2000L));

        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(newerRejected, captured));

        Payment result = paymentService.getStatus("leg-1", "leg-1", true);

        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(result.getProviderPaymentId()).isEqualTo("pay_1");
    }

    @Test
    void createOrder_capturesOwnerUserId() {
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_1", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1");

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void getStatus_returnsForOwner() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(
                Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        Payment result = paymentService.getStatus("leg-1", "user-1", false);

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void getStatus_throwsForNonOwnerNonAdmin() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(
                Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        assertThatThrownBy(() -> paymentService.getStatus("leg-1", "user-2", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getStatus_allowsAdminForAnyOwner() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(
                Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        Payment result = paymentService.getStatus("leg-1", "admin-user", true);

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void refund_prefersCapturedPaymentOverNewerRejectedPayment() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        captured.setCreatedAt(new Date(1000L));

        Payment newerRejected = createdPayment();
        newerRejected.setStatus("REJECTED");
        newerRejected.setCreatedAt(new Date(2000L));

        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(newerRejected, captured));
        when(paymentProvider.refund("pay_1", 150000L)).thenReturn(new RefundResult("SUCCESS", "rfnd_1"));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        assertThat(result.getProviderPaymentId()).isEqualTo("pay_1");
        verify(paymentProvider).refund("pay_1", 150000L);
    }
}
```

- [ ] **Step 6: Run the payment-service test suite**

Run: `cd microservices/payment-service && DOCKER_HOST=tcp://localhost:2375 JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" RAZORPAY_KEY_ID="rzp_test_dummy_key_id" RAZORPAY_KEY_SECRET="test-razorpay-key-secret-0123456789abcdef01" RAZORPAY_WEBHOOK_SECRET="test-razorpay-webhook-secret-0123456789abcd" ./mvnw clean verify`

Expected: `PaymentCaptureConcurrencyTest` will FAIL at this point — it still mocks `PaymentEventPublisher` and asserts `verify(eventPublisher, ...)`, which no longer applies since `PaymentService` no longer depends on it. This is expected; Task 2 rewrites that test file. For this step, run just the unit test to confirm Step 5's work: `./mvnw test -Dtest=PaymentServiceTest` (no Docker needed, pure Mockito) — expect all pass, same count as before (23 tests, same as pre-P1.4 baseline, since this task renames/adjusts existing assertions rather than adding new tests).

- [ ] **Step 7: Commit**

```bash
git add microservices/payment-service/src/main/resources/db/migration/V2__outbox.sql microservices/payment-service/src/main/java/com/travel2go/backend/model/OutboxEntry.java microservices/payment-service/src/main/java/com/travel2go/backend/repository/OutboxRepository.java microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java
git commit -m "P1.4: write outbox rows instead of afterCommit-publishing capture/refund events"
```

Note in the commit message or hand-back that `PaymentCaptureConcurrencyTest` is left temporarily red after this task — Task 2 fixes it as part of adding the relay. If your workflow requires every task to leave the suite fully green, run `./mvnw test -Dtest='!PaymentCaptureConcurrencyTest'` instead of the full `clean verify` for this task's own verification, and say so explicitly in your task report.

---

### Task 2: `OutboxRelay` + relay tests + fix `PaymentCaptureConcurrencyTest`

Adds the `@Scheduled` poller that actually publishes outbox rows, completing the pipe Task 1 started. Fixes the test Task 1 knowingly left red.

**Files:**
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/PaymentServiceApplication.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/service/OutboxRelay.java`
- Create: `microservices/payment-service/src/test/java/com/travel2go/backend/service/OutboxRelayTest.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentCaptureConcurrencyTest.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/PaymentServiceApplicationTests.java`

**Interfaces:**
- Consumes: `OutboxRepository.findBatchForPublishing(int limit)`, `OutboxEntry` (Task 1), `PaymentEventPublisher.publish(String routingKey, Object payload)` (pre-existing, unchanged), `PaymentCapturedEvent`/`PaymentRefundedEvent` (pre-existing).
- Produces: `OutboxRelay.relay()` — public, `@Transactional`, callable directly from tests (not just via `@Scheduled`) for deterministic testing.

- [ ] **Step 1: Enable scheduling**

```java
// microservices/payment-service/src/main/java/com/travel2go/backend/PaymentServiceApplication.java
package com.travel2go.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}
}
```

- [ ] **Step 2: Write the failing `OutboxRelayTest`**

```java
package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.OutboxEntry;
import com.travel2go.backend.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(properties = {
        "payment.provider=sandbox",
        "razorpay.webhook-secret=test-relay-webhook-secret-0123456789"
})
class OutboxRelayTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentEventPublisher eventPublisher;

    private OutboxEntry unpublishedEntry(String aggregateId) throws Exception {
        PaymentCapturedEvent event = new PaymentCapturedEvent(aggregateId, "pay_relay_1", 150000L);
        return outboxRepository.save(OutboxEntry.builder()
                .aggregateId(aggregateId)
                .type("payment.captured")
                .payload(objectMapper.writeValueAsString(event))
                .createdAt(new Date())
                .attempts(0)
                .build());
    }

    @Test
    void relay_publishesUnpublishedEntryAndMarksItPublished() throws Exception {
        OutboxEntry entry = unpublishedEntry("leg-relay-1");

        outboxRelay.relay();

        verify(eventPublisher, times(1)).publish(eq("payment.captured"),
                eq(new PaymentCapturedEvent("leg-relay-1", "pay_relay_1", 150000L)));
        OutboxEntry reloaded = outboxRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNotNull();
    }

    @Test
    void relay_skipsAlreadyPublishedEntries() throws Exception {
        OutboxEntry entry = unpublishedEntry("leg-relay-2");
        entry.setPublishedAt(new Date());
        outboxRepository.save(entry);

        outboxRelay.relay();

        verify(eventPublisher, times(0)).publish(any(), any());
    }

    @Test
    void relay_concurrentTicksOnSameRowPublishOnlyOnce() throws Exception {
        unpublishedEntry("leg-relay-3");

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    outboxRelay.relay();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        go.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd microservices/payment-service && DOCKER_HOST=tcp://localhost:2375 ./mvnw test -Dtest=OutboxRelayTest`
Expected: FAIL (compile error — `OutboxRelay` doesn't exist yet).

- [ ] **Step 4: Create `OutboxRelay`**

```java
package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.OutboxEntry;
import com.travel2go.backend.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Sole publisher of payment-service's domain events. Ticks every 1.5s,
 * pulling a small unpublished batch from the outbox (see OutboxRepository's
 * SKIP LOCKED query - safe under this service's multiple Cloud Run
 * instances) and publishing each. A publish failure leaves the row
 * unpublished and bumps its attempt count for the next tick - deliberately
 * no dead-lettering here, since giving up on a captured payment's
 * confirmation event is never acceptable; a permanently-down broker is an
 * ops incident, not something this relay should silently abandon.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 20;

    private final OutboxRepository outboxRepository;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1500)
    @Transactional
    public void relay() {
        List<OutboxEntry> batch = outboxRepository.findBatchForPublishing(BATCH_SIZE);
        for (OutboxEntry entry : batch) {
            try {
                Object event = deserialize(entry);
                eventPublisher.publish(entry.getType(), event);
                entry.setPublishedAt(new Date());
            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);
                log.error("Failed to relay outbox entry {} (type {}, attempt {}): {}",
                        entry.getId(), entry.getType(), entry.getAttempts(), e.getMessage(), e);
            }
            outboxRepository.save(entry);
        }
    }

    private Object deserialize(OutboxEntry entry) throws Exception {
        return switch (entry.getType()) {
            case "payment.captured" -> objectMapper.readValue(entry.getPayload(), PaymentCapturedEvent.class);
            case "payment.refunded" -> objectMapper.readValue(entry.getPayload(), PaymentRefundedEvent.class);
            default -> throw new IllegalStateException("Unknown outbox entry type: " + entry.getType());
        };
    }
}
```

- [ ] **Step 5: Run `OutboxRelayTest` to verify it passes**

Run: `cd microservices/payment-service && DOCKER_HOST=tcp://localhost:2375 ./mvnw test -Dtest=OutboxRelayTest`
Expected: PASS, 3/3.

- [ ] **Step 6: Fix `PaymentServiceApplicationTests`'s mock list**

`OutboxRelay` is now a `@Component` requiring `OutboxRepository`, which `PaymentServiceApplicationTests` doesn't provide (it excludes JPA/DataSource autoconfiguration entirely). Add `OutboxRepository` to its `@MockBean` list. Full replacement content of `microservices/payment-service/src/test/java/com/travel2go/backend/PaymentServiceApplicationTests.java`:

```java
package com.travel2go.backend;

import com.travel2go.backend.repository.OutboxRepository;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.repository.SettlementRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
    "payment.provider=sandbox",
    "razorpay.webhook-secret=test-context-load-webhook-secret-0123456789"
})
@MockBean({PaymentRepository.class, ProcessedWebhookEventRepository.class, RefundRepository.class,
        SettlementRepository.class, OutboxRepository.class})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
```

- [ ] **Step 7: Rewrite `PaymentCaptureConcurrencyTest` for the outbox+relay pipeline**

Replace the full content of `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentCaptureConcurrencyTest.java`:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.OutboxRepository;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "payment.provider=sandbox",
        "razorpay.webhook-secret=test-concurrency-webhook-secret-0123456789"
})
class PaymentCaptureConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockBean
    private PaymentProvider paymentProvider;

    private Payment persistCreatedPayment(String bookingRef, String providerOrderId) {
        Payment payment = Payment.builder()
                .bookingRef(bookingRef)
                .method("UPI")
                .status("CREATED")
                .amountPaise(150000L)
                .feePaise(0L)
                .providerRef(providerOrderId)
                .ownerUserId("user-1")
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();
        return paymentRepository.save(payment);
    }

    @Test
    void concurrentDeliveryOfSameEventWritesExactlyOneOutboxEntry() throws InterruptedException {
        persistCreatedPayment("leg-race-1", "order_race_1");
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_race_1", "pay_race_1", 150000L));

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    paymentService.applyWebhook("{}".getBytes(), Map.of());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }));
        }

        ready.await();
        go.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                fail("A concurrent applyWebhook call threw an exception - it must return normally "
                        + "for every losing thread", e.getCause());
            }
        }

        Payment result = paymentRepository.findByProviderRef("order_race_1").orElseThrow();
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(processedWebhookEventRepository.findById("pay_race_1")).isPresent();
        assertThat(outboxRepository.findAll().stream()
                .filter(e -> "leg-race-1".equals(e.getAggregateId())).count()).isEqualTo(1);
    }

    @Test
    void sequentialRedeliveryOfSameEventDoesNotSmearTimestampOrThrow() {
        persistCreatedPayment("leg-race-3", "order_race_3");
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_race_3", "pay_race_3", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());
        Date firstProcessedAt = processedWebhookEventRepository.findById("pay_race_3").orElseThrow().getProcessedAt();

        // Second, sequential delivery of the same event id: must not throw,
        // and - since it's a real INSERT ... ON CONFLICT DO NOTHING rather
        // than a save()-as-merge - must not silently overwrite processedAt.
        paymentService.applyWebhook("{}".getBytes(), Map.of());
        Date secondProcessedAt = processedWebhookEventRepository.findById("pay_race_3").orElseThrow().getProcessedAt();

        Payment result = paymentRepository.findByProviderRef("order_race_3").orElseThrow();
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(secondProcessedAt).isEqualTo(firstProcessedAt);
        assertThat(outboxRepository.findAll().stream()
                .filter(e -> "leg-race-3".equals(e.getAggregateId())).count()).isEqualTo(1);
    }

    @Test
    void secondEventIdTargetingAlreadyCapturedPaymentIsNoOp() {
        Payment payment = persistCreatedPayment("leg-race-2", "order_race_2");
        int firstCapture = paymentRepository.markCaptured(payment.getId(), "pay_first");
        assertThat(firstCapture).isEqualTo(1);

        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_race_2", "pay_second", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        assertThat(outboxRepository.findAll().stream()
                .filter(e -> "leg-race-2".equals(e.getAggregateId())).count()).isEqualTo(0);
        Payment result = paymentRepository.findByProviderRef("order_race_2").orElseThrow();
        assertThat(result.getProviderPaymentId()).isEqualTo("pay_first");
    }
}
```

Note: this test class no longer mocks `PaymentEventPublisher` at all — `PaymentService` doesn't depend on it anymore (Task 1), and this test doesn't invoke `OutboxRelay`, so the real `PaymentEventPublisher`/`RabbitTemplate` beans are present in the context but never exercised (no assertions depend on them, and `@Scheduled` firing in the background during this test is harmless — `OutboxRelay.relay()` may or may not tick during the test's execution window; either way, this test's assertions check outbox row existence/content, which is true whether or not a tick has already happened. `OutboxRelayTest`, not this class, is responsible for proving the relay itself works.).

- [ ] **Step 8: Run the full payment-service suite**

Run: `cd microservices/payment-service && DOCKER_HOST=tcp://localhost:2375 JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" RAZORPAY_KEY_ID="rzp_test_dummy_key_id" RAZORPAY_KEY_SECRET="test-razorpay-key-secret-0123456789abcdef01" RAZORPAY_WEBHOOK_SECRET="test-razorpay-webhook-secret-0123456789abcd" ./mvnw clean verify`
Expected: BUILD SUCCESS, all tests pass (the previous 43, minus nothing, plus `OutboxRelayTest`'s 3 new tests = 46; exact count may differ slightly if Task 1's rewrite changed method names 1:1 — trust your own count over this estimate).

- [ ] **Step 9: Commit**

```bash
git add microservices/payment-service
git commit -m "P1.4: add OutboxRelay scheduled poller, publishing outbox entries via SKIP LOCKED"
```

---

### Task 3: Trip-service publishes `leg.confirmed` after confirming the `Leg`

Producer side of the choreography change. Trip-service's existing `payment.captured` binding is untouched; it now additionally publishes `leg.confirmed` (best-effort, per spec Decision 1 — no outbox, since trip-service has no Postgres) after a successful `Leg` confirmation.

**Files:**
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/service/LegConfirmedEvent.java`
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java`
- Modify: `microservices/trip-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`

**Interfaces:**
- Produces: `LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise)` (record, `com.travel2go.backend.service` package — trip-service's producer-side package, mirroring where `PaymentCapturedEvent`/`PaymentRefundedEvent` live in payment-service).
- Consumed by Task 4: this event's exact field names/types, and the routing key `"leg.confirmed"` it's published under (via `TripEventPublisher.publish("leg.confirmed", event)`, existing class, unchanged).

- [ ] **Step 1: Create `LegConfirmedEvent`**

```java
package com.travel2go.backend.service;

public record LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise) {
}
```

- [ ] **Step 2: Write the failing test**

Add this test to `microservices/trip-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java` (add the new imports and `@Mock private TripEventPublisher tripEventPublisher;` field, and pass it into the constructor call in `setUp()` — see Step 4 for the exact updated file):

```java
    @Test
    void onPaymentCaptured_confirmingLegPublishesLegConfirmed() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").pricePaise(150000L).build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(tripEventPublisher).publish("leg.confirmed",
                new com.travel2go.backend.service.LegConfirmedEvent("leg-1", "pay_1", 150000L));
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd microservices/trip-service && ./mvnw test -Dtest=PaymentCapturedConsumerTest`
Expected: FAIL (compile error — `PaymentCapturedConsumer`'s constructor doesn't accept a `TripEventPublisher` yet).

- [ ] **Step 4: Modify `PaymentCapturedConsumer`**

Replace the full content of `microservices/trip-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java`:

```java
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
```

- [ ] **Step 5: Rewrite `PaymentCapturedConsumerTest`**

Replace the full content of `microservices/trip-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`:

```java
package com.travel2go.backend.consumer;

import com.travel2go.backend.model.Leg;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.service.LegConfirmedEvent;
import com.travel2go.backend.service.TripEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCapturedConsumerTest {

    @Mock private LegRepository legRepository;
    @Mock private TripEventPublisher tripEventPublisher;

    private PaymentCapturedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentCapturedConsumer(legRepository, tripEventPublisher);
        lenient().when(legRepository.save(any(Leg.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void onPaymentCaptured_confirmsPendingLeg() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").pricePaise(150000L).build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(legRepository).save(argThat(l -> "CONFIRMED".equals(l.getStatus())));
    }

    @Test
    void onPaymentCaptured_confirmingLegPublishesLegConfirmed() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").pricePaise(150000L).build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(tripEventPublisher).publish("leg.confirmed",
                new LegConfirmedEvent("leg-1", "pay_1", 150000L));
    }

    @Test
    void onPaymentCaptured_amountMismatchDoesNotConfirm() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").pricePaise(150000L).build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 999L));

        verify(legRepository, never()).save(any());
        verify(tripEventPublisher, never()).publish(any(), any());
    }

    @Test
    void onPaymentCaptured_duplicateDeliveryIsNoOp() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));
        leg.setStatus("CONFIRMED");
        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(legRepository, times(1)).save(any());
        verify(tripEventPublisher, times(1)).publish(any(), any());
    }

    @Test
    void onPaymentCaptured_unknownLegThrows() {
        when(legRepository.findById("leg-unknown")).thenReturn(Mono.empty());

        assertThatThrownBy(() ->
                consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-unknown", "pay_1", 150000L)))
                .isInstanceOf(IllegalStateException.class);

        verify(legRepository, never()).save(any());
        verify(tripEventPublisher, never()).publish(any(), any());
    }

    @Test
    void onPaymentCaptured_unexpectedExceptionPropagates() {
        when(legRepository.findById("leg-1")).thenThrow(new RuntimeException("transient Firestore error"));

        assertThatThrownBy(() ->
                consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("transient Firestore error");

        verify(legRepository, never()).save(any());
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd microservices/trip-service && ./mvnw clean verify` (with the required env vars: `JWT_SECRET`, `QUOTE_TOKEN_SECRET`, matching this service's `application.properties`)
Expected: BUILD SUCCESS. Note `onPaymentCaptured_unknownLegThrows` and `onPaymentCaptured_unexpectedExceptionPropagates` REPLACE the old `..._unknownLegDoesNotThrow`/`..._unexpectedExceptionIsCaughtAndDoesNotPropagate` tests — this is intentional (spec Decision 7); the old assertions are the exact behavior being removed.

- [ ] **Step 7: Commit**

```bash
git add microservices/trip-service/src/main/java/com/travel2go/backend/service/LegConfirmedEvent.java microservices/trip-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java microservices/trip-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java
git commit -m "P1.4: trip-service confirms Leg then publishes leg.confirmed; unknown-leg/unexpected errors now propagate"
```

---

### Task 4: Booking-service — DLQ/retry + re-bind to `leg.confirmed`

Consumer side of the choreography change, plus this service's DLQ/retry infrastructure.

**Files:**
- Create: `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/LegConfirmedEvent.java`
- Delete: `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedEvent.java`
- Delete: `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java`
- Create: `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/LegConfirmedConsumer.java`
- Delete: `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`
- Create: `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/LegConfirmedConsumerTest.java`
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`
- Modify: `microservices/booking-service/src/main/resources/application.properties`
- Modify: `microservices/booking-service/pom.xml`
- Create: `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/LegConfirmedDlqIntegrationTest.java`

**Interfaces:**
- Consumes: `LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise)` produced by Task 3 (trip-service), routing key `"leg.confirmed"`.
- Produces (consumer-local copy, `com.travel2go.backend.consumer` package, mirrors the removed `PaymentCapturedEvent`'s pattern exactly): `LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise)`.
- Queue rename: `booking.payment-captured` → `booking.leg-confirmed`; routing key `payment.captured` → `leg.confirmed`; new DLX `booking.dlx`; new DLQ named `booking.leg-confirmed.dlq` (matches the renamed main queue, not the old `payment-captured` name).

- [ ] **Step 1: Add the Testcontainers `rabbitmq` dependency**

Add to `microservices/booking-service/pom.xml`'s `<dependencies>` block, alongside the existing `spring-boot-starter-test`:

```xml
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>rabbitmq</artifactId>
			<version>1.19.7</version>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>junit-jupiter</artifactId>
			<version>1.19.7</version>
			<scope>test</scope>
		</dependency>
```

- [ ] **Step 2: Add retry properties**

Add to `microservices/booking-service/src/main/resources/application.properties`, after the existing `# RabbitMQ Configuration` block:

```properties
# --- DLQ + bounded retry (P1.4): an unrecoverable message is dead-lettered
# after 4 attempts, never silently dropped, never infinitely requeued. ---
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=4
spring.rabbitmq.listener.simple.retry.initial-interval=1000
spring.rabbitmq.listener.simple.retry.multiplier=2
spring.rabbitmq.listener.simple.retry.max-interval=10000
spring.rabbitmq.listener.simple.default-requeue-rejected=false
```

- [ ] **Step 3: Create `LegConfirmedEvent` (consumer-local copy)**

```java
package com.travel2go.backend.consumer;

public record LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise) {
}
```

- [ ] **Step 4: Delete the old event record and consumer**

Delete `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedEvent.java` and `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java` (fully replaced by Steps 3 and 5 — booking-service no longer consumes `payment.captured` at all).

- [ ] **Step 5: Create `LegConfirmedConsumer`**

```java
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
```

- [ ] **Step 6: Update `RabbitMQConfig`**

Replace the `// --- P1.2: consumer side of payment-service's trip.exchange fan-out ---` section at the bottom of `microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java` (everything from that comment to the end of the class body, before the closing `}`) with:

```java
    // --- P1.4: consumer side of trip-service's leg.confirmed choreography event ---

    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }

    @Bean
    public FanoutExchange bookingDlx() {
        return new FanoutExchange("booking.dlx");
    }

    @Bean
    public Queue bookingLegConfirmedDlq() {
        return new Queue("booking.leg-confirmed.dlq", true);
    }

    @Bean
    public Binding bookingLegConfirmedDlqBinding() {
        return BindingBuilder.bind(bookingLegConfirmedDlq()).to(bookingDlx());
    }

    @Bean
    public Queue bookingLegConfirmedQueue() {
        return QueueBuilder.durable("booking.leg-confirmed")
                .withArgument("x-dead-letter-exchange", "booking.dlx")
                .build();
    }

    @Bean
    public Binding bookingLegConfirmedBinding() {
        return BindingBuilder.bind(bookingLegConfirmedQueue()).to(tripExchange()).with("leg.confirmed");
    }
```

Add `import org.springframework.amqp.core.FanoutExchange;` and `import org.springframework.amqp.core.QueueBuilder;` to the file's imports.

Update the `jsonMessageConverter()` bean's `idClassMapping` (booking-service no longer consumes `PaymentCapturedEvent`, it consumes `LegConfirmedEvent` now — update the audit-trail mapping to match):

```java
        typeMapper.setIdClassMapping(java.util.Map.of(
                "com.travel2go.backend.service.LegConfirmedEvent",
                com.travel2go.backend.consumer.LegConfirmedEvent.class));
```

(This replaces the old `idClassMapping` entry that mapped `"com.travel2go.backend.service.PaymentCapturedEvent"` — trip-service's `LegConfirmedEvent` lives in `com.travel2go.backend.service`, per Task 3.)

- [ ] **Step 7: Delete the old test file, add the new one**

Delete `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`.

Create `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/LegConfirmedConsumerTest.java`:

```java
package com.travel2go.backend.consumer;

import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegConfirmedConsumerTest {

    @Mock private BookingRepository bookingRepository;

    private LegConfirmedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LegConfirmedConsumer(bookingRepository);
        lenient().when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void onLegConfirmed_confirmsPendingBooking() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository).save(argThat(b ->
                "CONFIRMED".equals(b.getStatus()) && "pay_1".equals(b.getProviderPaymentId()) && b.getConfirmedAt() != null));
    }

    @Test
    void onLegConfirmed_duplicateDeliveryConfirmsOnlyOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));
        pending.setStatus("CONFIRMED");
        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository, times(1)).save(any());
    }

    @Test
    void onLegConfirmed_amountMismatchDoesNotConfirm() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 999L));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void onLegConfirmed_unknownLegIdThrows() {
        when(bookingRepository.findByLegId("leg-unknown")).thenReturn(Flux.empty());

        assertThatThrownBy(() ->
                consumer.onLegConfirmed(new LegConfirmedEvent("leg-unknown", "pay_1", 150000L)))
                .isInstanceOf(IllegalStateException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void onLegConfirmed_prefersActiveBookingOverStaleRejectedOne() {
        Booking rejected = Booking.builder().id("b0").legId("leg-1").status("REJECTED").amountPaise(150000L).build();
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(rejected, pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository).save(argThat(b ->
                "b1".equals(b.getId()) && "CONFIRMED".equals(b.getStatus())));
    }

    @Test
    void onLegConfirmed_unexpectedExceptionPropagates() {
        when(bookingRepository.findByLegId("leg-1")).thenThrow(new RuntimeException("transient Firestore error"));

        assertThatThrownBy(() ->
                consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("transient Firestore error");

        verify(bookingRepository, never()).save(any());
    }
}
```

- [ ] **Step 8: Write the failing DLQ integration test**

```java
package com.travel2go.backend.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.awaitility.Awaitility.await;

/**
 * Proves the DLQ + bounded retry wiring end to end against a real RabbitMQ:
 * a message that always fails processing (findByLegId throws) is retried
 * 4 times with backoff, then lands in booking.leg-confirmed.dlq - never
 * silently dropped, never infinitely requeued.
 */
@Testcontainers
@SpringBootTest(properties = {
        "jwt.secret=test-jwt-secret-0123456789abcdef0123456789abcdef",
        "quote.token.secret=test-quote-token-secret-0123456789abcdef0123456789abcdef",
        "spring.cloud.gcp.firestore.enabled=false",
        "spring.cloud.gcp.storage.enabled=false",
        "spring.cloud.gcp.core.enabled=false"
})
class LegConfirmedDlqIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingRepository bookingRepository;

    @Test
    void unrecoverableMessage_isDeadLetteredAfterExhaustedRetries() throws Exception {
        when(bookingRepository.findByLegId("leg-dlq-1")).thenReturn(Flux.error(new RuntimeException("always fails")));

        LegConfirmedEvent event = new LegConfirmedEvent("leg-dlq-1", "pay_dlq_1", 150000L);
        rabbitTemplate.convertAndSend("trip.exchange", "leg.confirmed", event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Object dlqMessage = rabbitTemplate.receiveAndConvert("booking.leg-confirmed.dlq", 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(dlqMessage).isInstanceOf(LegConfirmedEvent.class);
            assertThat(((LegConfirmedEvent) dlqMessage).legId()).isEqualTo("leg-dlq-1");
        });
    }
}
```

Add the `awaitility` test dependency needed by this test to `microservices/booking-service/pom.xml`'s `<dependencies>` block:

```xml
		<dependency>
			<groupId>org.awaitility</groupId>
			<artifactId>awaitility</artifactId>
			<version>4.2.1</version>
			<scope>test</scope>
		</dependency>
```

- [ ] **Step 9: Run the tests to verify they fail, then pass**

Run: `cd microservices/booking-service && ./mvnw test -Dtest=LegConfirmedConsumerTest`
Expected: PASS (this one doesn't depend on the DLQ config, only the consumer class from Step 5).

Run: `cd microservices/booking-service && DOCKER_HOST=tcp://localhost:2375 ./mvnw test -Dtest=LegConfirmedDlqIntegrationTest` (needs Docker; this machine reaches it at `tcp://localhost:2375` with `${user.home}/.docker-java.properties` already set to `api.version=1.44`)
Expected: PASS — the message is retried 4 times (visible in logs as repeated `findByLegId` invocations with growing gaps) then appears in `booking.leg-confirmed.dlq`, found by the `await()` poll.

- [ ] **Step 10: Run the full booking-service suite**

Run: `cd microservices/booking-service && DOCKER_HOST=tcp://localhost:2375 JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" ./mvnw clean verify` (add any other required env vars this service's `application.properties` needs — check for unresolved `${...}` placeholders with no default first)
Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add microservices/booking-service
git commit -m "P1.4: booking-service re-binds to leg.confirmed, adds DLQ + bounded retry"
```

---

### Task 5: Trip-service — DLQ/retry on its own `payment.captured` consumer

This is trip-service's OWN consumer of `payment.captured` (unchanged binding from Task 3) gaining the same DLQ/retry infrastructure Task 4 gave booking-service. Independent of Task 4 — order between them doesn't matter, but this plan sequences it last since it depends on Task 3's `PaymentCapturedConsumer` already being in its post-choreography-change state.

**Files:**
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`
- Modify: `microservices/trip-service/src/main/resources/application.properties`
- Modify: `microservices/trip-service/pom.xml`
- Create: `microservices/trip-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedDlqIntegrationTest.java`

**Interfaces:**
- Consumes: `PaymentCapturedConsumer` from Task 3 (unchanged by this task — only the queue's DLX/DLQ wiring changes, not the consumer class itself).

- [ ] **Step 1: Add the Testcontainers `rabbitmq` and `awaitility` dependencies**

Add to `microservices/trip-service/pom.xml`'s `<dependencies>` block:

```xml
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>rabbitmq</artifactId>
			<version>1.19.7</version>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>junit-jupiter</artifactId>
			<version>1.19.7</version>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.awaitility</groupId>
			<artifactId>awaitility</artifactId>
			<version>4.2.1</version>
			<scope>test</scope>
		</dependency>
```

- [ ] **Step 2: Add retry properties**

Add to `microservices/trip-service/src/main/resources/application.properties`, after the existing RabbitMQ config block:

```properties
# --- DLQ + bounded retry (P1.4): an unrecoverable message is dead-lettered
# after 4 attempts, never silently dropped, never infinitely requeued. ---
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=4
spring.rabbitmq.listener.simple.retry.initial-interval=1000
spring.rabbitmq.listener.simple.retry.multiplier=2
spring.rabbitmq.listener.simple.retry.max-interval=10000
spring.rabbitmq.listener.simple.default-requeue-rejected=false
```

- [ ] **Step 3: Update `RabbitMQConfig`**

Replace the bottom section of `microservices/trip-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java` (from `@Bean public TopicExchange tripExchange()` to the end of the class) with:

```java
    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }

    @Bean
    public FanoutExchange tripDlx() {
        return new FanoutExchange("trip.dlx");
    }

    @Bean
    public Queue tripPaymentCapturedDlq() {
        return new Queue("trip.payment-captured.dlq", true);
    }

    @Bean
    public Binding tripPaymentCapturedDlqBinding() {
        return BindingBuilder.bind(tripPaymentCapturedDlq()).to(tripDlx());
    }

    @Bean
    public Queue tripPaymentCapturedQueue() {
        return QueueBuilder.durable("trip.payment-captured")
                .withArgument("x-dead-letter-exchange", "trip.dlx")
                .build();
    }

    @Bean
    public Binding tripPaymentCapturedBinding() {
        return BindingBuilder.bind(tripPaymentCapturedQueue()).to(tripExchange()).with("payment.captured");
    }
}
```

Add `import org.springframework.amqp.core.FanoutExchange;` and `import org.springframework.amqp.core.QueueBuilder;` to the file's imports (the closing `}` above ends the class — make sure you're replacing through to that final brace and not leaving a duplicate).

- [ ] **Step 4: Write the failing DLQ integration test**

```java
package com.travel2go.backend.consumer;

import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.service.TripEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.awaitility.Awaitility.await;

/**
 * Proves trip-service's DLQ + bounded retry wiring end to end against a
 * real RabbitMQ, mirroring booking-service's LegConfirmedDlqIntegrationTest.
 */
@Testcontainers
@SpringBootTest(properties = {
        "jwt.secret=test-jwt-secret-0123456789abcdef0123456789abcdef",
        "quote.token.secret=test-quote-token-secret-0123456789abcdef0123456789abcdef",
        "spring.cloud.gcp.firestore.enabled=false",
        "spring.cloud.gcp.storage.enabled=false",
        "spring.cloud.gcp.core.enabled=false"
})
class PaymentCapturedDlqIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private LegRepository legRepository;

    @MockBean
    private TripEventPublisher tripEventPublisher;

    @Test
    void unrecoverableMessage_isDeadLetteredAfterExhaustedRetries() {
        when(legRepository.findById("leg-dlq-1")).thenReturn(Mono.error(new RuntimeException("always fails")));

        PaymentCapturedEvent event = new PaymentCapturedEvent("leg-dlq-1", "pay_dlq_1", 150000L);
        rabbitTemplate.convertAndSend("trip.exchange", "payment.captured", event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Object dlqMessage = rabbitTemplate.receiveAndConvert("trip.payment-captured.dlq", 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(dlqMessage).isInstanceOf(PaymentCapturedEvent.class);
            assertThat(((PaymentCapturedEvent) dlqMessage).bookingRef()).isEqualTo("leg-dlq-1");
        });
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd microservices/trip-service && DOCKER_HOST=tcp://localhost:2375 ./mvnw test -Dtest=PaymentCapturedDlqIntegrationTest`
Expected: PASS.

- [ ] **Step 6: Run the full trip-service suite**

Run: `cd microservices/trip-service && DOCKER_HOST=tcp://localhost:2375 JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" ./mvnw clean verify` (add any other required env vars this service's `application.properties` needs)
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add microservices/trip-service
git commit -m "P1.4: trip-service adds DLQ + bounded retry to its payment.captured consumer"
```

---

## Final acceptance check (maps to the design spec's criteria)

1. `grep -rn 'afterCommit\|registerSynchronization' microservices/payment-service/src/main --include='*.java'` → no matches (Task 1 removes it entirely).
2. `ls microservices/payment-service/src/main/resources/db/migration/` → `V1__money_ledger.sql`, `V2__outbox.sql` both present (Task 1).
3. `grep -rn 'outbox\|Outbox\|SKIP LOCKED\|@Scheduled' microservices/payment-service/src/main --include='*.java' --include='*.sql'` → hits in `OutboxEntry`, `OutboxRepository`, `OutboxRelay`, `V2__outbox.sql`, `PaymentServiceApplication` (Tasks 1-2).
4. `grep -rn 'dead-letter\|x-dead-letter\|\.dlq\|retry.enabled' microservices/booking-service/src/main microservices/trip-service/src/main --include='*.java' --include='*.properties'` → hits in both services' `RabbitMQConfig`/`application.properties` (Tasks 4-5).
5. `grep -rn 'leg.confirmed\|payment.captured' microservices/*/src/main --include='*.java'` → trip-service publishes+consumes `payment.captured`... actually publishes `leg.confirmed`, consumes `payment.captured`; booking-service now only references `leg.confirmed`; payment-service still only publishes `payment.captured`/`payment.refunded` via the outbox (Tasks 3-4).
6. `cd microservices/payment-service && DOCKER_HOST=tcp://localhost:2375 ./mvnw clean verify` → BUILD SUCCESS (Testcontainers: outbox relay + redelivery, Tasks 1-2).
7. `cd microservices/booking-service && DOCKER_HOST=tcp://localhost:2375 ./mvnw clean verify` → BUILD SUCCESS (Task 4).
8. `cd microservices/trip-service && DOCKER_HOST=tcp://localhost:2375 ./mvnw clean verify` → BUILD SUCCESS (Tasks 3, 5).
