# P1.3: Postgres Money Ledger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move payment-service's `Payment` and `ProcessedWebhookEvent` off Firestore onto Cloud SQL Postgres, close the webhook-capture check-then-act race with a DB-enforced atomic transaction, and add `refunds`/`settlements` tables plus a `ROLE_ADMIN` reconciliation endpoint.

**Architecture:** Flyway-managed Postgres schema (`payments`, `processed_webhook_events`, `refunds`, `settlements`) backing new JPA entities in payment-service's own `com.travel2go.backend.model`/`.webhook` packages (same package names as the Firestore versions they replace, so most call sites need no import changes). `PaymentService` becomes fully synchronous. Webhook capture becomes one `@Transactional` method: insert-into-dedupe-table-or-noop, then a conditional `UPDATE ... WHERE status='CREATED'`, with the `payment.captured` publish deferred to `afterCommit()` via `TransactionSynchronizationManager`.

**Tech Stack:** Spring Data JPA, Flyway, PostgreSQL (Cloud SQL via socket-factory connector), Testcontainers (Postgres) for the concurrency proof.

## Global Constraints

- G1: `fee_paise` must always be `0` — enforced both at the JPA entity default and the `NOT NULL DEFAULT 0` schema column.
- Event contract `PaymentCapturedEvent(bookingRef, providerPaymentId, amountPaise)` and `PaymentRefundedEvent(bookingRef, providerRefundId, amountPaise)` stay byte-identical — no consumer in trip-service or booking-service may need a code change.
- No in-source secret/URL defaults (A2 pattern) — `spring.datasource.url`/`username`/`password` have no property defaults in `application.properties`.
- Cloud SQL region is `asia-south2` (matches `infra/terraform/variables.tf`'s `region` default), matching every other piece of this system's data localization.
- `spring.jpa.hibernate.ddl-auto=validate` — Flyway owns schema; Hibernate only validates.
- Firestore stays untouched for every other collection in every other service — this plan only ever touches `microservices/payment-service` and `infra/terraform`/`.github/workflows/backend-deploy.yml`.

---

### Task 1: JPA/Postgres foundation — entities, migration, repositories, service lift-and-shift

Migrates payment-service from Firestore to Postgres/JPA while preserving exact existing behavior (no atomicity fix yet — that is Task 2). This is a mechanical "de-reactify" of the whole module so it compiles and all existing tests pass against the new stack.

**Files:**
- Modify: `microservices/payment-service/pom.xml`
- Modify: `microservices/payment-service/src/main/resources/application.properties`
- Create: `microservices/payment-service/src/main/resources/db/migration/V1__money_ledger.sql`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/model/Payment.java` (JPA entity, replaces the common-models one)
- Delete: `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/webhook/ProcessedWebhookEvent.java` (Firestore `@Document` -> JPA `@Entity`)
- Delete: `microservices/payment-service/src/main/java/com/travel2go/backend/config/FirestoreConfig.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/webhook/ProcessedWebhookEventRepository.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/PaymentServiceApplicationTests.java`

**Interfaces:**
- Produces: `Payment` (JPA `@Entity`, `id` is `UUID`, fields: `bookingRef`, `method`, `status`, `amountPaise` (`Long`), `feePaise` (`Long`, default `0L`), `providerRef` (maps to column `provider_order_id`), `providerPaymentId`, `quoteTokenValidated` (`Boolean`), `ownerUserId`, `createdAt`/`updatedAt` (`Date`)) — same field names/getters as the old Firestore entity, so `PaymentController`/`PaymentConflictException` call sites are untouched.
- Produces: `PaymentRepository extends JpaRepository<Payment, UUID>` with `List<Payment> findByBookingRef(String)` and `Optional<Payment> findByProviderRef(String)`.
- Produces: `ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, String>`.
- Consumed by Task 2: `PaymentRepository` (Task 2 adds a `markCaptured` method to it), `PaymentService` (Task 2 rewrites `applyWebhook`'s CAPTURED branch only).
- Consumed by Task 4: `Payment.getId()` returns `UUID`, used as the FK type for `Refund.paymentId`/`Settlement.paymentId`.

- [ ] **Step 1: Update `pom.xml` — add JPA/Postgres/Flyway/Testcontainers, remove Firestore**

Replace the `spring-cloud-gcp-starter-data-firestore` dependency block:

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>
		<dependency>
			<groupId>com.google.cloud.sql</groupId>
			<artifactId>postgres-socket-factory</artifactId>
			<version>1.19.1</version>
		</dependency>
```

And add to the existing `<dependencies>` block (test scope, alongside `spring-boot-starter-test`):

```xml
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>postgresql</artifactId>
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

The `com.google.cloud` / `spring-cloud-gcp-dependencies` BOM import in `<dependencyManagement>` can stay (harmless if nothing from it is used, and removing it risks disturbing the `spring-cloud.version` BOM import next to it — leave both BOM imports as-is, only remove the one dependency declaration that pulled Firestore).

- [ ] **Step 2: Write the Flyway migration**

```sql
-- microservices/payment-service/src/main/resources/db/migration/V1__money_ledger.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_ref            TEXT NOT NULL,
    owner_user_id          TEXT,
    status                 TEXT NOT NULL,
    amount_paise           BIGINT NOT NULL,
    fee_paise              BIGINT NOT NULL DEFAULT 0,
    method                 TEXT,
    provider_order_id      TEXT,
    provider_payment_id    TEXT,
    quote_token_validated  BOOLEAN,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ,
    CONSTRAINT uq_payments_provider_order_id UNIQUE (provider_order_id)
);

CREATE UNIQUE INDEX uq_payments_provider_payment_id
    ON payments (provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE INDEX idx_payments_booking_ref ON payments (booking_ref);
CREATE INDEX idx_payments_owner_user_id ON payments (owner_user_id);

CREATE TABLE processed_webhook_events (
    event_id     TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refunds (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id            UUID NOT NULL REFERENCES payments(id),
    provider_refund_id    TEXT,
    amount_paise          BIGINT NOT NULL,
    status                TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);

CREATE TABLE settlements (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id               UUID NOT NULL REFERENCES payments(id),
    provider_settlement_id   TEXT,
    amount_paise             BIGINT NOT NULL,
    settled_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                   TEXT NOT NULL
);

CREATE INDEX idx_settlements_payment_id ON settlements (payment_id);
```

- [ ] **Step 3: Delete the old Firestore `Payment` and create the JPA `Payment` entity**

Delete `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`.

Create `microservices/payment-service/src/main/java/com/travel2go/backend/model/Payment.java`:

```java
package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_ref", nullable = false)
    private String bookingRef;

    private String method; // UPI | CARD | NETBANKING
    private String status; // CREATED | CAPTURED | FAILED | REJECTED | REFUNDED

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "fee_paise", nullable = false)
    @Builder.Default
    private Long feePaise = 0L; // MUST always be 0 (G1)

    @Column(name = "provider_order_id")
    private String providerRef; // provider ORDER id (set at CREATED)

    @Column(name = "provider_payment_id")
    private String providerPaymentId; // provider PAYMENT id (set on CAPTURED, from the webhook)

    @Column(name = "quote_token_validated")
    private Boolean quoteTokenValidated;

    @Column(name = "owner_user_id")
    private String ownerUserId; // captured from the JWT at createOrder time (P1.2)

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = new Date();
    }
}
```

- [ ] **Step 4: Convert `ProcessedWebhookEvent` to a JPA entity**

Replace the full content of `microservices/payment-service/src/main/java/com/travel2go/backend/webhook/ProcessedWebhookEvent.java`:

```java
package com.travel2go.backend.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Idempotency record: one row per provider payment id already captured, so a
 * re-delivered "payment.captured" webhook is a no-op instead of a
 * double-capture / double-publish. The primary key IS the atomic dedupe -
 * inserting a duplicate id throws a constraint violation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {
    @Id
    @Column(name = "event_id")
    private String id; // the provider payment id

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "processed_at", nullable = false)
    private Date processedAt;
}
```

- [ ] **Step 5: Delete `FirestoreConfig`**

Delete `microservices/payment-service/src/main/java/com/travel2go/backend/config/FirestoreConfig.java` (it builds a `FirestoreOptions` bean from a class that no longer exists on the classpath once the Firestore starter is removed).

- [ ] **Step 6: Rewrite `PaymentRepository`**

```java
package com.travel2go.backend.repository;

import com.travel2go.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingRef(String bookingRef);

    Optional<Payment> findByProviderRef(String providerRef);
}
```

- [ ] **Step 7: Rewrite `ProcessedWebhookEventRepository`**

```java
package com.travel2go.backend.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, String> {
}
```

- [ ] **Step 8: Rewrite `PaymentService` — synchronous, same behavior as before**

Replace the full content of `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`. This step preserves the exact check-then-act semantics of the original webhook handling (Task 2 makes it atomic) — only the reactive plumbing (`Flux`/`Mono`/`.block()`) is removed:

```java
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
        if (dedupeKey != null && processedWebhookEventRepository.findById(dedupeKey).isPresent()) {
            log.info("Webhook for payment {} already processed, skipping", dedupeKey);
            return;
        }

        Payment payment = paymentRepository.findByProviderRef(event.getProviderOrderId()).orElse(null);

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
            paymentRepository.save(payment);

            if (dedupeKey != null) {
                processedWebhookEventRepository.save(
                        ProcessedWebhookEvent.builder().id(dedupeKey).processedAt(new Date()).build());
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
            paymentRepository.save(payment);
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
        Payment saved = paymentRepository.save(payment);

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

- [ ] **Step 9: Update `application.properties`**

Remove lines 4-5 (`spring.cloud.gcp.firestore.project-id` / `.database-id`) and add datasource/JPA/Flyway config. Full replacement content:

```properties
spring.application.name=payment-service
server.port=${PORT:8080}
server.forward-headers-strategy=framework

spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true

jwt.secret=${JWT_SECRET}
jwt.expiration=86400000

quote.token.secret=${QUOTE_TOKEN_SECRET}
quote.token.ttl-ms=900000

management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always

payment.provider=razorpay

razorpay.key-id=${RAZORPAY_KEY_ID}
razorpay.key-secret=${RAZORPAY_KEY_SECRET}
razorpay.webhook-secret=${RAZORPAY_WEBHOOK_SECRET}

spring.rabbitmq.host=${SPRING_RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${SPRING_RABBITMQ_PORT:5672}
spring.rabbitmq.username=${SPRING_RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${SPRING_RABBITMQ_PASSWORD:guest}
spring.rabbitmq.virtual-host=${SPRING_RABBITMQ_VIRTUAL_HOST:/}
spring.rabbitmq.ssl.enabled=${SPRING_RABBITMQ_SSL_ENABLED:false}
```

(`spring.jpa.open-in-view=false` is a standard, harmless default-off for a stateless REST service with no lazy-loaded associations rendered in views — added here since Spring Boot otherwise logs a warning about it being enabled by default.)

- [ ] **Step 10: Rewrite `PaymentServiceApplicationTests` (context-load smoke test)**

The full application context must load without a real Postgres instance. Exclude datasource/JPA/Flyway autoconfiguration entirely (mirrors the existing `spring.cloud.gcp.firestore.enabled=false` pattern used for the Firestore version) and mock the repositories:

```java
package com.travel2go.backend;

import com.travel2go.backend.repository.PaymentRepository;
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
@MockBean({PaymentRepository.class, ProcessedWebhookEventRepository.class})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
```

- [ ] **Step 11: Rewrite `PaymentServiceTest` for the synchronous JPA repository shape**

Same test cases, same assertions as before — only the mocking style changes (`List`/`Optional` instead of `Flux`/`Mono`):

```java
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;
    @Mock private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, processedWebhookEventRepository, paymentProvider, quoteTokenService, eventPublisher);
        lenient().when(paymentRepository.save(any(Payment.class)))
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
    void applyWebhook_capturedTransitionsPaymentAndPublishesEvent() {
        Payment payment = createdPayment();
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(processedWebhookEventRepository.findById("pay_1")).thenReturn(Optional.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository).save(argThat(p -> "CAPTURED".equals(p.getStatus()) && "pay_1".equals(p.getProviderPaymentId())));
        verify(eventPublisher).publish(eq("payment.captured"),
                eq(new PaymentCapturedEvent("leg-1", "pay_1", 150000L)));
    }

    @Test
    void applyWebhook_duplicateCapturedIsIdempotent_publishesEventOnlyOnce() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(createdPayment()));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.findById("pay_1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ProcessedWebhookEvent.builder().id("pay_1").processedAt(new Date()).build()));
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        paymentService.applyWebhook("{}".getBytes(), Map.of());
        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }

    @Test
    void applyWebhook_amountMismatchDoesNotCapture() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(createdPayment()));
        when(processedWebhookEventRepository.findById("pay_1")).thenReturn(Optional.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 999L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any());
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
        verify(eventPublisher).publish(eq("payment.refunded"),
                eq(new PaymentRefundedEvent("leg-1", "rfnd_1", 150000L)));
    }

    @Test
    void refund_isIdempotentOnAlreadyRefunded() {
        Payment refunded = createdPayment();
        refunded.setStatus("REFUNDED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(refunded));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(paymentProvider, never()).refund(any(), anyLong());
        verify(eventPublisher, never()).publish(eq("payment.refunded"), any());
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

        // Newer payment listed first, to prove "newest" isn't blindly picked.
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

- [ ] **Step 12: Run the full module test suite**

Run: `cd microservices/common-models && ./mvnw -q clean install && cd ../platform-security && ./mvnw -q clean install && cd ../payment-service && ./mvnw clean verify`
Expected: BUILD SUCCESS, all `PaymentServiceTest`/`PaymentServiceApplicationTests`/`PaymentControllerWebhookTest`/`RazorpayProviderTest`/`SandboxPaymentProviderTest` tests pass (`PaymentControllerWebhookTest` and the provider tests are untouched by this task and must still pass unmodified).

- [ ] **Step 13: Commit**

```bash
git add microservices/payment-service microservices/common-models
git commit -m "P1.3: migrate payment-service Payment/ProcessedWebhookEvent from Firestore to Postgres/JPA"
```

---

### Task 2: Atomic webhook-capture idempotency

Closes the check-then-act race left open by Task 1's lift-and-shift. This is the ticket's core fix: one `@Transactional` method does an insert-or-noop dedupe write, then a single conditional `UPDATE ... WHERE status='CREATED'`, and defers the `payment.captured` publish to after the transaction commits.

**Files:**
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `Payment`, `PaymentRepository`, `ProcessedWebhookEventRepository`, `PaymentEventPublisher` from Task 1 (unchanged signatures except the new `markCaptured` method added here).
- Produces: `PaymentRepository.markCaptured(UUID id, String providerPaymentId)` returning `int` (rows affected) — this is the atomic guard; `0` means the row was not in `CREATED` status (already handled by someone else).
- Consumed by Task 3: the exact `applyWebhook` behavior under concurrency is what Task 3's Testcontainers test proves.

- [ ] **Step 1: Add the failing/new test cases to `PaymentServiceTest`**

Add these two tests (the existing `applyWebhook_*` tests from Task 1 stay as regression coverage and must still pass with the new mocking below):

```java
    @Test
    void applyWebhook_duplicateEventIdIsNoOpEvenIfPaymentStillCreated() {
        // Simulates two different webhook deliveries racing: the second one's
        // dedupe-insert hits the unique constraint before it ever reaches the
        // conditional UPDATE.
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).findByProviderRef(any());
        verify(paymentRepository, never()).markCaptured(any(), any());
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void applyWebhook_captureRaceLoserIsNoOp_whenMarkCapturedAffectsZeroRows() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(0);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(eventPublisher, never()).publish(any(), any());
    }
```

Update the existing `applyWebhook_capturedTransitionsPaymentAndPublishesEvent` and `applyWebhook_duplicateCapturedIsIdempotent_publishesEventOnlyOnce` and `applyWebhook_amountMismatchDoesNotCapture` tests to the new mocking shape (`saveAndFlush`/`markCaptured` instead of `findById`/`save`):

```java
    @Test
    void applyWebhook_capturedTransitionsPaymentAndPublishesEvent() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(1);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository).markCaptured(payment.getId(), "pay_1");
        verify(eventPublisher).publish(eq("payment.captured"),
                eq(new PaymentCapturedEvent("leg-1", "pay_1", 150000L)));
    }

    @Test
    void applyWebhook_duplicateCapturedIsIdempotent_publishesEventOnlyOnce() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(1);

        paymentService.applyWebhook("{}".getBytes(), Map.of());
        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }

    @Test
    void applyWebhook_amountMismatchDoesNotCapture() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(createdPayment()));
        when(processedWebhookEventRepository.saveAndFlush(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 999L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).markCaptured(any(), any());
        verify(eventPublisher, never()).publish(any(), any());
    }
```

- [ ] **Step 2: Run tests to verify the new/changed ones fail**

Run: `cd microservices/payment-service && ./mvnw test -Dtest=PaymentServiceTest`
Expected: FAIL — `markCaptured` and `saveAndFlush` are not yet stubbable/callable the way the tests expect (compile error: `markCaptured` doesn't exist on `PaymentRepository` yet).

- [ ] **Step 3: Add `markCaptured` to `PaymentRepository`**

```java
package com.travel2go.backend.repository;

import com.travel2go.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingRef(String bookingRef);

    Optional<Payment> findByProviderRef(String providerRef);

    /**
     * Atomically transitions a payment CREATED -> CAPTURED. The WHERE clause
     * is the race guard: only one concurrent caller's UPDATE can match a row
     * still in CREATED, so at most one of them ever sees a return value > 0.
     */
    @Modifying
    @Query("UPDATE Payment p SET p.status = 'CAPTURED', p.providerPaymentId = :providerPaymentId, "
            + "p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id AND p.status = 'CREATED'")
    int markCaptured(@Param("id") UUID id, @Param("providerPaymentId") String providerPaymentId);
}
```

- [ ] **Step 4: Rewrite `PaymentService.applyWebhook`**

Replace the `applyWebhook` method and add the two new private helpers, in `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`. Add these imports: `org.springframework.dao.DataIntegrityViolationException`, `org.springframework.transaction.annotation.Transactional`, `org.springframework.transaction.support.TransactionSynchronization`, `org.springframework.transaction.support.TransactionSynchronizationManager`.

```java
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
            try {
                processedWebhookEventRepository.saveAndFlush(
                        ProcessedWebhookEvent.builder().id(dedupeKey).processedAt(new Date()).build());
            } catch (DataIntegrityViolationException e) {
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
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    eventPublisher.publish("payment.captured", toPublish);
                } catch (Exception e) {
                    log.error("Failed to publish payment.captured event for bookingRef {} providerPaymentId {}: {}",
                            toPublish.bookingRef(), toPublish.providerPaymentId(), e.getMessage(), e);
                }
            }
        });
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
```

Remove the old `applyWebhook` body's inline logic entirely (fully replaced by the three methods above).

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd microservices/payment-service && ./mvnw test -Dtest=PaymentServiceTest`
Expected: PASS, all `applyWebhook_*` tests green, including the two new race-guard tests.

- [ ] **Step 6: Full module verify**

Run: `./mvnw clean verify` (from `microservices/payment-service`)
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add microservices/payment-service
git commit -m "P1.3: make webhook-capture idempotency atomic via DB unique constraint + conditional UPDATE"
```

---

### Task 3: Testcontainers concurrency proof

Proves, against a real Postgres instance, that Task 2's atomic capture actually closes the race — something no Mockito-mocked test can demonstrate. Requires Docker Desktop running locally.

**Files:**
- Create: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentCaptureConcurrencyTest.java`

**Interfaces:**
- Consumes: `PaymentService.applyWebhook`, `PaymentRepository`, `ProcessedWebhookEventRepository`, `Payment`, `PaymentProvider`, `WebhookEvent`/`WebhookEventType` from Tasks 1-2 (unchanged).

- [ ] **Step 1: Write the concurrency test**

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
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

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @MockBean
    private PaymentProvider paymentProvider;

    @MockBean
    private PaymentEventPublisher eventPublisher;

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
    void concurrentDeliveryOfSameEventCapturesExactlyOnce() throws InterruptedException {
        persistCreatedPayment("leg-race-1", "order_race_1");
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_race_1", "pay_race_1", 150000L));

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    paymentService.applyWebhook("{}".getBytes(), Map.of());
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

        Payment result = paymentRepository.findByProviderRef("order_race_1").orElseThrow();
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(processedWebhookEventRepository.findById("pay_race_1")).isPresent();
        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }

    @Test
    void secondEventIdTargetingAlreadyCapturedPaymentIsNoOp() throws InterruptedException {
        Payment payment = persistCreatedPayment("leg-race-2", "order_race_2");
        int firstCapture = paymentRepository.markCaptured(payment.getId(), "pay_first");
        assertThat(firstCapture).isEqualTo(1);

        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_race_2", "pay_second", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(eventPublisher, times(0)).publish(any(), any());
        Payment result = paymentRepository.findByProviderRef("order_race_2").orElseThrow();
        assertThat(result.getProviderPaymentId()).isEqualTo("pay_first");
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd microservices/payment-service && ./mvnw test -Dtest=PaymentCaptureConcurrencyTest`
Expected: PASS. Requires Docker Desktop running locally (Testcontainers pulls/starts `postgres:15-alpine`). If it fails with a Docker-connection error, start Docker Desktop and re-run.

- [ ] **Step 3: Run the full module suite once more**

Run: `./mvnw clean verify` (from `microservices/payment-service`)
Expected: BUILD SUCCESS, including this new test class alongside all others.

- [ ] **Step 4: Commit**

```bash
git add microservices/payment-service
git commit -m "P1.3: add Testcontainers proof that concurrent webhook capture is exactly-once"
```

---

### Task 4: Refund ledger row + reconciliation endpoint

Full-scope addition (user-approved): `refund()` now records a `Refund` row transactionally; a new `ROLE_ADMIN` reconciliation endpoint reports captured/refunded/settled totals and lists captured payments with no matching settlement.

**Files:**
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/model/Refund.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/model/Settlement.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/RefundRepository.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/SettlementRepository.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/service/ReconciliationSummary.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/service/ReconciliationService.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/controller/ReconciliationController.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/security/SecurityConfig.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`
- Create: `microservices/payment-service/src/test/java/com/travel2go/backend/service/ReconciliationServiceTest.java`
- Create: `microservices/payment-service/src/test/java/com/travel2go/backend/controller/ReconciliationControllerTest.java`

**Interfaces:**
- Produces: `Refund` entity (`id` UUID, `paymentId` UUID, `providerRefundId`, `amountPaise` Long, `status`, `createdAt` Date), `RefundRepository extends JpaRepository<Refund, UUID>`.
- Produces: `Settlement` entity (same shape plus `settledAt`), `SettlementRepository extends JpaRepository<Settlement, UUID>`.
- Produces: `ReconciliationSummary(long totalCapturedPaise, long totalRefundedPaise, long totalSettledPaise, List<Payment> unsettledCaptures)`, `ReconciliationService.getSummary()`.
- Produces: `GET /api/payments/reconciliation`, `ROLE_ADMIN`-gated.

- [ ] **Step 1: Create the `Refund` entity**

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

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    private String status; // SUCCESS | FAILED

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
```

- [ ] **Step 2: Create the `Settlement` entity**

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

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "settlements")
public class Settlement {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "provider_settlement_id")
    private String providerSettlementId;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "settled_at", nullable = false)
    private Date settledAt;

    private String status;
}
```

- [ ] **Step 3: Create `RefundRepository` and `SettlementRepository`**

```java
// microservices/payment-service/src/main/java/com/travel2go/backend/repository/RefundRepository.java
package com.travel2go.backend.repository;

import com.travel2go.backend.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByPaymentId(UUID paymentId);

    @Query("SELECT COALESCE(SUM(r.amountPaise), 0) FROM Refund r WHERE r.status = 'SUCCESS'")
    long sumRefundedAmountPaise();
}
```

```java
// microservices/payment-service/src/main/java/com/travel2go/backend/repository/SettlementRepository.java
package com.travel2go.backend.repository;

import com.travel2go.backend.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    List<Settlement> findByPaymentId(UUID paymentId);

    @Query("SELECT COALESCE(SUM(s.amountPaise), 0) FROM Settlement s")
    long sumSettledAmountPaise();
}
```

- [ ] **Step 4: Add reconciliation queries to `PaymentRepository`**

Add to the interface body of `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java` (keep everything from Task 2):

```java
    @Query("SELECT COALESCE(SUM(p.amountPaise), 0) FROM Payment p WHERE p.status = 'CAPTURED'")
    long sumCapturedAmountPaise();

    @Query("SELECT p FROM Payment p WHERE p.status = 'CAPTURED' AND p.id NOT IN "
            + "(SELECT s.paymentId FROM Settlement s)")
    List<Payment> findCapturedWithoutSettlement();
```

(Add `import com.travel2go.backend.model.Settlement;` to this file's imports.)

- [ ] **Step 5: Write the failing `ReconciliationServiceTest`**

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private SettlementRepository settlementRepository;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(paymentRepository, refundRepository, settlementRepository);
    }

    @Test
    void getSummary_aggregatesTotalsAndUnsettledCaptures() {
        Payment unsettled = Payment.builder().bookingRef("leg-1").status("CAPTURED").amountPaise(150000L).build();
        when(paymentRepository.sumCapturedAmountPaise()).thenReturn(500000L);
        when(refundRepository.sumRefundedAmountPaise()).thenReturn(150000L);
        when(settlementRepository.sumSettledAmountPaise()).thenReturn(350000L);
        when(paymentRepository.findCapturedWithoutSettlement()).thenReturn(List.of(unsettled));

        ReconciliationSummary summary = reconciliationService.getSummary();

        assertThat(summary.totalCapturedPaise()).isEqualTo(500000L);
        assertThat(summary.totalRefundedPaise()).isEqualTo(150000L);
        assertThat(summary.totalSettledPaise()).isEqualTo(350000L);
        assertThat(summary.unsettledCaptures()).containsExactly(unsettled);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd microservices/payment-service && ./mvnw test -Dtest=ReconciliationServiceTest`
Expected: FAIL (compile error — `ReconciliationSummary`/`ReconciliationService` don't exist yet).

- [ ] **Step 7: Create `ReconciliationSummary` and `ReconciliationService`**

```java
// microservices/payment-service/src/main/java/com/travel2go/backend/service/ReconciliationSummary.java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;

import java.util.List;

public record ReconciliationSummary(
        long totalCapturedPaise,
        long totalRefundedPaise,
        long totalSettledPaise,
        List<Payment> unsettledCaptures) {
}
```

```java
// microservices/payment-service/src/main/java/com/travel2go/backend/service/ReconciliationService.java
package com.travel2go.backend.service;

import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final SettlementRepository settlementRepository;

    public ReconciliationSummary getSummary() {
        return new ReconciliationSummary(
                paymentRepository.sumCapturedAmountPaise(),
                refundRepository.sumRefundedAmountPaise(),
                settlementRepository.sumSettledAmountPaise(),
                paymentRepository.findCapturedWithoutSettlement());
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd microservices/payment-service && ./mvnw test -Dtest=ReconciliationServiceTest`
Expected: PASS.

- [ ] **Step 9: Make `refund()` write a `Refund` row transactionally**

In `PaymentService.java`, add a new field `private final RefundRepository refundRepository;` directly after the existing `processedWebhookEventRepository` field declaration (Lombok `@RequiredArgsConstructor` generates the constructor in field-declaration order, so this fixes the resulting parameter order at position 3 — see Step 9's `setUp()` update below, which passes `refundRepository` in that exact position). Add `import com.travel2go.backend.model.Refund;` and `import com.travel2go.backend.repository.RefundRepository;` (the `Transactional`/`TransactionSynchronizationManager`/`TransactionSynchronization` imports already exist from Task 2). Then replace the `refund` method body:

```java
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

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    eventPublisher.publish("payment.refunded",
                            new PaymentRefundedEvent(bookingRef, result.getProviderRefundId(), saved.getAmountPaise()));
                } catch (Exception e) {
                    log.error("Failed to publish payment.refunded event for bookingRef {} providerRefundId {}: {}",
                            bookingRef, result.getProviderRefundId(), e.getMessage(), e);
                }
            }
        });

        return saved;
    }
```

Update the two `refund_*` tests in `PaymentServiceTest` that call `paymentService.refund(...)` and assert on `eventPublisher.publish` — add `@Mock private RefundRepository refundRepository;` to the test's field list, pass it into the `new PaymentService(...)` constructor call in `setUp()` (after `paymentRepository`, matching field declaration order — Lombok generates the constructor in field-declaration order, so `refundRepository` must be added to `PaymentService.java` directly after `paymentRepository` for the existing `new PaymentService(paymentRepository, processedWebhookEventRepository, ...)` call sites in tests to keep compiling positionally; alternatively, since this is fragile, switch the test's `setUp()` constructor call to name the exact updated parameter list), and add `lenient().when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));` to `setUp()`.

Concretely, `PaymentService`'s field order after this step is: `paymentRepository, processedWebhookEventRepository, refundRepository, paymentProvider, quoteTokenService, eventPublisher`. Update `PaymentServiceTest.setUp()`:

```java
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock private com.travel2go.backend.repository.RefundRepository refundRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;
    @Mock private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, processedWebhookEventRepository, refundRepository,
                paymentProvider, quoteTokenService, eventPublisher);
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(refundRepository.save(any(com.travel2go.backend.model.Refund.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(processedWebhookEventRepository.findById(any(String.class))).thenReturn(Optional.empty());
        lenient().when(paymentRepository.findByBookingRef(any())).thenReturn(List.of());
    }
```

- [ ] **Step 10: Run the full `PaymentServiceTest` suite**

Run: `cd microservices/payment-service && ./mvnw test -Dtest=PaymentServiceTest`
Expected: PASS, including `refund_transitionsCapturedToRefunded` and `refund_prefersCapturedPaymentOverNewerRejectedPayment`.

- [ ] **Step 11: Add the `ROLE_ADMIN` matcher to `SecurityConfig`**

In `microservices/payment-service/src/main/java/com/travel2go/backend/security/SecurityConfig.java`, add one line to the `authorizeHttpRequests` chain, alongside the existing refund matcher:

```java
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/refund").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/reconciliation").hasAuthority("ROLE_ADMIN")
```

- [ ] **Step 12: Write the failing `ReconciliationControllerTest`**

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.security.JwtUtil;
import com.travel2go.backend.service.PaymentService;
import com.travel2go.backend.service.ReconciliationService;
import com.travel2go.backend.service.ReconciliationSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReconciliationController.class)
@org.springframework.context.annotation.Import(com.travel2go.backend.security.SecurityConfig.class)
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void reconciliation_requiresAdmin_returns403ForNonAdmin() throws Exception {
        when(jwtUtil.extractUsername(any())).thenReturn("test-user");
        when(jwtUtil.extractRoles(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/payments/reconciliation")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconciliation_returnsSummaryForAdmin() throws Exception {
        when(jwtUtil.extractUsername(any())).thenReturn("admin-user");
        when(jwtUtil.extractRoles(any())).thenReturn(List.of("ROLE_ADMIN"));
        when(reconciliationService.getSummary())
                .thenReturn(new ReconciliationSummary(500000L, 150000L, 350000L, List.of()));

        mockMvc.perform(get("/api/payments/reconciliation")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapturedPaise").value(500000))
                .andExpect(jsonPath("$.totalRefundedPaise").value(150000))
                .andExpect(jsonPath("$.totalSettledPaise").value(350000));
    }
}
```

- [ ] **Step 13: Run test to verify it fails**

Run: `cd microservices/payment-service && ./mvnw test -Dtest=ReconciliationControllerTest`
Expected: FAIL (compile error — `ReconciliationController` doesn't exist).

- [ ] **Step 14: Create `ReconciliationController`**

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.service.ReconciliationService;
import com.travel2go.backend.service.ReconciliationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @GetMapping
    public ReconciliationSummary summary() {
        return reconciliationService.getSummary();
    }
}
```

- [ ] **Step 15: Run test to verify it passes, then run the full module suite**

Run: `cd microservices/payment-service && ./mvnw clean verify`
Expected: BUILD SUCCESS, all tests including the new `ReconciliationServiceTest`/`ReconciliationControllerTest` pass.

- [ ] **Step 16: Commit**

```bash
git add microservices/payment-service
git commit -m "P1.3: add refund ledger rows and ROLE_ADMIN reconciliation endpoint"
```

---

### Task 5: Terraform — Cloud SQL provisioning

Provisions the Postgres instance, database, user, and IAM binding. No local test — `terraform validate`/`plan` is the check; `apply` remains a manual hand-back item, consistent with A4's SA provisioning in this same repo.

**Files:**
- Create: `infra/terraform/cloudsql.tf`
- Modify: `infra/terraform/variables.tf`

**Interfaces:**
- Produces: Terraform outputs `payment_db_instance_connection_name`, `payment_db_name` — consumed by Task 6's `backend-deploy.yml` wiring (as a human-copied value into a `INSTANCE_CONNECTION_NAME` GitHub secret, since `backend-deploy.yml` does not read Terraform state directly).

- [ ] **Step 1: Add the DB password variable**

Append to `infra/terraform/variables.tf`:

```hcl
variable "payment_db_password" {
  type        = string
  description = "Password for the t2g_payments Postgres app user. Provide via TF_VAR_payment_db_password or a tfvars file that is NOT committed."
  sensitive   = true
}
```

- [ ] **Step 2: Write `cloudsql.tf`**

```hcl
# Cloud SQL for payment-service's Postgres money ledger (P1.3). No other
# service reads from this instance - Firestore remains the datastore for
# every other collection in every other service.

resource "google_sql_database_instance" "payments" {
  name             = "t2g-payments"
  project          = var.project_id
  region           = var.region
  database_version = "POSTGRES_15"

  settings {
    tier = "db-f1-micro"
    ip_configuration {
      ipv4_enabled = true
    }
    backup_configuration {
      enabled = true
    }
  }

  deletion_protection = true
}

resource "google_sql_database" "payments_db" {
  name     = "t2g_payments"
  project  = var.project_id
  instance = google_sql_database_instance.payments.name
}

resource "google_sql_user" "payments_app_user" {
  name     = "t2g_payment_app"
  project  = var.project_id
  instance = google_sql_database_instance.payments.name
  password = var.payment_db_password
}

# Lets t2g-payment's Cloud Run instance reach this DB via the Cloud SQL
# JDBC Socket Factory (--add-cloudsql-instances), no VPC connector needed.
resource "google_project_iam_member" "payment_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.svc["payment"].email}"
}

output "payment_db_instance_connection_name" {
  value = google_sql_database_instance.payments.connection_name
}

output "payment_db_name" {
  value = google_sql_database.payments_db.name
}
```

- [ ] **Step 3: Validate**

Run: `cd infra/terraform && terraform init -backend=false && terraform validate`
Expected: `Success! The configuration is valid.`

- [ ] **Step 4: Commit**

```bash
git add infra/terraform/cloudsql.tf infra/terraform/variables.tf
git commit -m "P1.3: provision Cloud SQL Postgres instance for payment-service's money ledger"
```

Hand back to the user: `terraform apply` still needs to be run manually (requires `TF_VAR_payment_db_password` and real GCP credentials), same as A4's outstanding SA-provisioning item.

---

### Task 6: Wire Cloud SQL into the deploy workflow

Adds the socket-factory connector flag and DB env vars to payment-service's Cloud Run deploy step only. No local test — this is infra YAML, verified by structural review (matches F1/F2's precedent for workflow-file changes).

**Files:**
- Modify: `.github/workflows/backend-deploy.yml`

**Interfaces:**
- Consumes: Task 5's Terraform outputs (`payment_db_instance_connection_name`, `payment_db_name`) as values a human pastes into new GitHub repository secrets (`DB_INSTANCE_CONNECTION_NAME`) after running `terraform apply` — this plan does not automate that hand-off.

- [ ] **Step 1: Add DB secrets to the "Prepare Environment Variables" step**

In `.github/workflows/backend-deploy.yml`, add these three lines to the `set_env` block (after the `FINAL_RAZORPAY_WEBHOOK_SECRET` line):

```bash
          set_env "FINAL_DB_URL" "${{ secrets.PAYMENT_DB_URL }}" "placeholder_db_url"
          set_env "FINAL_DB_USERNAME" "${{ secrets.PAYMENT_DB_USERNAME }}" "placeholder_db_username"
          set_env "FINAL_DB_PASSWORD" "${{ secrets.PAYMENT_DB_PASSWORD }}" "placeholder_db_password"
```

(`PAYMENT_DB_URL` is the full JDBC URL including the `cloudSqlInstance`/`socketFactory` query params, e.g. `jdbc:postgresql:///t2g_payments?cloudSqlInstance=travel2go-495007:asia-south2:t2g-payments&socketFactory=com.google.cloud.sql.postgres.SocketFactory` — a GitHub secret set manually after `terraform apply`, not derived in this workflow.)

- [ ] **Step 2: Add the DB env vars to payment-service's case block**

In the "Build service-scoped env vars" step's `RAZORPAY_*` case block, add immediately after it:

```bash
            # DB_* -> payment-service only (Postgres money ledger, P1.3).
            case "$SVC" in
              payment-service)
                echo "DB_URL=${FINAL_DB_URL}"
                echo "DB_USERNAME=${FINAL_DB_USERNAME}"
                echo "DB_PASSWORD=${FINAL_DB_PASSWORD}" ;;
            esac
```

- [ ] **Step 3: Add `--add-cloudsql-instances` to payment-service's Cloud Run flags**

In the "Set Cloud Run flags per service" step, the existing `elif` branch already covers `payment-service` (grouped with `trip-service`/`booking-service` for internal-only access). Split `payment-service` out so it gets the extra flag without changing the other two services:

Replace:
```bash
          elif [ "$SVC" = "trip-service" ] || [ "$SVC" = "payment-service" ] || [ "$SVC" = "booking-service" ]; then
            # Internal-only (--no-allow-unauthenticated). Combined with the
            # per-caller run.invoker bindings in infra/terraform/run-invoker.tf,
            # this now gives true isolation: only the intended caller SAs can
            # invoke these, not "any workload in the project".
            echo "CR_FLAGS=--port=8080 --no-allow-unauthenticated --min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost ${RUN_SA} ${STARTUP_PROBE}" >> $GITHUB_ENV
```

With:
```bash
          elif [ "$SVC" = "payment-service" ]; then
            # Same internal-only posture as trip/booking, plus the Cloud SQL
            # socket-factory connector (P1.3) so this service can reach its
            # Postgres money ledger without a VPC connector.
            echo "CR_FLAGS=--port=8080 --no-allow-unauthenticated --min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost --add-cloudsql-instances=${{ secrets.DB_INSTANCE_CONNECTION_NAME }} ${RUN_SA} ${STARTUP_PROBE}" >> $GITHUB_ENV
          elif [ "$SVC" = "trip-service" ] || [ "$SVC" = "booking-service" ]; then
            # Internal-only (--no-allow-unauthenticated). Combined with the
            # per-caller run.invoker bindings in infra/terraform/run-invoker.tf,
            # this now gives true isolation: only the intended caller SAs can
            # invoke these, not "any workload in the project".
            echo "CR_FLAGS=--port=8080 --no-allow-unauthenticated --min-instances=0 --max-instances=3 --concurrency=80 --timeout=300 --cpu-boost ${RUN_SA} ${STARTUP_PROBE}" >> $GITHUB_ENV
```

- [ ] **Step 4: Review the diff structurally**

Run: `git diff .github/workflows/backend-deploy.yml`
Confirm: only `payment-service` gained `--add-cloudsql-instances` and the three `DB_*` env vars; `trip-service`/`booking-service`'s flags are byte-identical to before; every other service's block is untouched.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/backend-deploy.yml
git commit -m "P1.3: wire Cloud SQL socket-factory connector and DB secrets into payment-service's deploy step"
```

Hand back to the user: three new GitHub repository secrets must be set manually before this deploy step will work — `PAYMENT_DB_URL`, `PAYMENT_DB_USERNAME`, `PAYMENT_DB_PASSWORD` (matching the app user created in Task 5), and `DB_INSTANCE_CONNECTION_NAME` (Task 5's `payment_db_instance_connection_name` Terraform output). Until then the placeholder defaults keep the workflow syntactically valid but a real deploy of payment-service will fail to connect to the DB.

---

## Final acceptance check (maps to the design spec's criteria)

1. `grep -r FirestoreReactiveRepository microservices/payment-service` → no matches (Task 1).
2. `ls microservices/payment-service/src/main/resources/db/migration/` → `V1__money_ledger.sql` exists (Task 1).
3. `grep -E "UNIQUE|PRIMARY KEY" microservices/payment-service/src/main/resources/db/migration/V1__money_ledger.sql` → shows `provider_order_id`, `provider_payment_id`, `event_id` constraints (Task 1).
4. `grep cloudsql.client infra/terraform/cloudsql.tf` → binding present (Task 5; `terraform apply` itself is a manual hand-back item).
5. `./mvnw test -Dtest=PaymentCaptureConcurrencyTest` passes (Task 3).
6. `grep -n "record PaymentCapturedEvent" microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentCapturedEvent.java` → unchanged signature (never touched by this plan).
7. `grep -E "ddl-auto|flyway.enabled" microservices/payment-service/src/main/resources/application.properties` → `validate` / `true` (Task 1); `./mvnw clean verify` passes with Testcontainers (Task 3).
8. `curl -X GET .../api/payments/reconciliation` with a `ROLE_ADMIN` token returns the summary shape; without it, 403 (Task 4).
