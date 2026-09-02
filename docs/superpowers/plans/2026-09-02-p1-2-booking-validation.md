# P1.2 Booking Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** booking-service independently re-validates the quote-token before persisting a leg booking (never trusting trip-service's caller), persists it as `PENDING`, and confirms it — and the corresponding trip-service `Leg` — only when a verified `payment.captured` event arrives. Closes the P1.1-review IDOR on payment-service's `getStatus`.

**Architecture:** `LegBookingController` thins into a `LegBookingService` (mirrors `PaymentService` from P1.1). A new `PaymentCapturedConsumer` in booking-service and a separate, independent one in trip-service both subscribe to the same `payment.captured` event payment-service already publishes on `trip.exchange` (fan-out, not a synchronous callback chain) — booking-service's confirms the `Booking` and notifies; trip-service's flips the matching `Leg` from a new `PENDING` status to `CONFIRMED`. Both are idempotent via a status-guard, same pattern P1.1's final review validated for payment-service itself.

**Tech Stack:** Spring Boot 3.2.4 / Java 17, Spring AMQP (`@RabbitListener`, already-declared `trip.exchange`), Firestore reactive repositories, the shared `QuoteTokenService`/`JwtUtil` (`platform-security`), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- **G1:** `feePaise` is always `0L` on the leg-booking path. Unchanged from existing code.
- **G2:** the quote-token gate — `quoteTokenService.isValid(token, legId, amountPaise)` — must run and must pass before any `Booking` other than a `REJECTED` audit record is persisted.
- **Never set `Booking.status="CONFIRMED"` or `Leg.status="CONFIRMED"` at creation time.** That is the entire point of this plan. Confirmation happens only in the two new `payment.captured` consumers.
- **`legId` is the canonical leg-booking reference** end-to-end: what `LegBookingResponse` returns, what trip-service stores as `Leg.supplierRef`, what the client uses as `bookingRef` when calling `payment-service`'s `POST /api/payments/order`, and what both new consumers key their `Payment.bookingRef`/`event.bookingRef()` lookups on.
- **Idempotency mechanism for both new consumers is a status-guard** (`if status != PENDING, no-op`) — no separate dedupe collection. This mirrors the pattern P1.1's final whole-branch review independently verified for `payment-service`'s own consumer.
- **A2 fail-fast pattern:** `booking-service`'s new `quote.token.secret` property is `${QUOTE_TOKEN_SECRET}` with no in-source default — the shared `QuoteTokenService`'s existing `@PostConstruct` already fail-fasts at <32 bytes; nothing new to add there.
- **A3 scoping:** `QUOTE_TOKEN_SECRET` reaches `booking-service` via the existing per-service case block in `backend-deploy.yml` — never broadened beyond the three services that need it (`trip-service`, `payment-service`, `booking-service`).
- **Legacy `POST /api/bookings`** (package-booking path, `BookingController`, `permitAll`) is untouched by every task in this plan.
- **Ownership checks return `404`** (not `403`) for both a missing record and a non-owner's access attempt — this avoids confirming to an unauthorized caller that a given `legId`/`bookingRef` exists at all. `ROLE_ADMIN` bypasses ownership on both new checks (payment-service `getStatus`, booking-service `GET /api/leg-bookings/{legId}`).
- **RabbitMQ cross-service message contract:** `payment-service` publishes its `PaymentCapturedEvent` record via `Jackson2JsonMessageConverter`, which by default stamps a `__TypeId__` header containing the *sender's* fully-qualified class name (`com.travel2go.backend.service.PaymentCapturedEvent`). Both new consumers declare their **own local** `PaymentCapturedEvent` record (same field names/order, different package) — a plain `Jackson2JsonMessageConverter` bean on the consumer side would try to `Class.forName()` the sender's class name and throw `ClassNotFoundException` at every message. Each consuming service's `RabbitMQConfig` must configure a `DefaultJackson2JavaTypeMapper` with an explicit `idClassMapping` from the sender's class name string to the local record class — this is done in Tasks 4 and 6 below, not optional.

---

### Task 1: Model changes (`common-models`)

**Files:**
- Modify: `microservices/common-models/src/main/java/com/travel2go/backend/model/Booking.java`
- Modify: `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`

**Interfaces:**
- Produces: `Booking.getOwnerUserId()/setOwnerUserId(String)`, `.getProviderPaymentId()/.setProviderPaymentId(String)`, `.getConfirmedAt()/.setConfirmedAt(Date)`; `Payment.getOwnerUserId()/.setOwnerUserId(String)` — used by every later task in this plan.

Pure data-shape task, no behavior to unit test. Verify by compiling both `booking-service` and `payment-service` after installing the updated `common-models` jar.

- [ ] **Step 1: Add the three new fields to Booking**

Replace `microservices/common-models/src/main/java/com/travel2go/backend/model/Booking.java` with:

```java
package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "bookings")
public class Booking {
    @DocumentId
    private String id;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;

    private String packageId;
    private String packageTitle;

    private Date bookingDate;
    private String status;

    // Leg-level booking fields (trip-service orchestration) - null for legacy package bookings
    private String tripId;
    private String legId;
    private String quoteToken;
    private Long amountPaise;
    private Long feePaise;
    private String ownerUserId; // captured from the JWT at create time (P1.2)
    private String providerPaymentId; // set on CONFIRMED, from the payment.captured event (P1.2)
    private Date confirmedAt; // set on CONFIRMED (P1.2)
}
```

- [ ] **Step 2: Add ownerUserId to Payment**

In `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`, add one field after `quoteTokenValidated`:

```java
    private Boolean quoteTokenValidated;
    private String ownerUserId; // captured from the JWT at createOrder time (P1.2)
```

- [ ] **Step 3: Rebuild common-models and verify booking-service/payment-service still compile**

```bash
cd microservices/common-models && ./mvnw -q clean install
cd ../booking-service && ./mvnw -q clean compile
cd ../payment-service && ./mvnw -q clean compile
```
Expected: `BUILD SUCCESS` for all three (this task is purely additive — nothing references the new fields yet, so nothing should break).

- [ ] **Step 4: Commit**

```bash
git add microservices/common-models/src/main/java/com/travel2go/backend/model/Booking.java
git add microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java
git commit -m "P1.2: add ownerUserId/providerPaymentId/confirmedAt to Booking, ownerUserId to Payment"
```

---

### Task 2: booking-service RabbitMQ + quote-token config

**Files:**
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`
- Modify: `microservices/booking-service/src/main/resources/application.properties`
- Modify: `.github/workflows/backend-deploy.yml`

**Interfaces:**
- Produces: a `TopicExchange` bean named `tripExchange` (bean method `tripExchange()`, exchange name `"trip.exchange"`) and a durable `Queue` bean `bookingPaymentCapturedQueue` (queue name `"booking.payment-captured"`) bound to it with routing key `"payment.captured"` — consumed by Task 4's `@RabbitListener`. Activates the shared `QuoteTokenService` bean (via `quote.token.secret`) — consumed by Task 3.

`booking-service` currently declares only its own pre-existing `booking.exchange` (`DirectExchange`, unrelated, outbound `booking.initiated`). This task adds `trip.exchange` alongside it — both exchanges coexist, nothing about the existing one changes.

- [ ] **Step 1: Add the trip.exchange declaration and the new queue/binding**

Replace `microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java` with:

```java
package com.travel2go.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue}")
    private String queue;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    @Autowired
    public void configureVirtualHost(ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory) {
            CachingConnectionFactory cachingFactory = (CachingConnectionFactory) connectionFactory;
            if ("/".equals(cachingFactory.getVirtualHost()) && !"guest".equals(cachingFactory.getUsername())) {
                System.out.println("Booking-Service: Overriding default virtual host '/' to '"
                        + cachingFactory.getUsername() + "' for CloudAMQP compatibility");
                cachingFactory.setVirtualHost(cachingFactory.getUsername());
            }
        }
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange bookingExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue bookingQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public Binding bookingBinding() {
        return BindingBuilder.bind(bookingQueue()).to(bookingExchange()).with(routingKey);
    }

    // --- P1.2: consumer side of payment-service's trip.exchange fan-out ---

    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }

    @Bean
    public Queue bookingPaymentCapturedQueue() {
        return new Queue("booking.payment-captured", true);
    }

    @Bean
    public Binding bookingPaymentCapturedBinding() {
        return BindingBuilder.bind(bookingPaymentCapturedQueue()).to(tripExchange()).with("payment.captured");
    }
}
```

Note: Task 4 will replace the plain `jsonMessageConverter()` bean above with a type-mapped one once the local `PaymentCapturedEvent` record exists — that edit happens in Task 4, not here.

- [ ] **Step 2: Add quote.token.secret to application.properties**

Append to `microservices/booking-service/src/main/resources/application.properties`:

```properties

# --- Quote-token validation (P1.2): independent re-check of trip-signed tokens ---
quote.token.secret=${QUOTE_TOKEN_SECRET}
```

- [ ] **Step 3: Scope QUOTE_TOKEN_SECRET to booking-service in the deploy workflow**

In `.github/workflows/backend-deploy.yml`, find:

```bash
            # QUOTE_TOKEN_SECRET -> only the two services that sign/verify quote tokens.
            case "$SVC" in
              trip-service|payment-service)
                echo "QUOTE_TOKEN_SECRET=${FINAL_QUOTE_TOKEN_SECRET}" ;;
            esac
```

Replace with:

```bash
            # QUOTE_TOKEN_SECRET -> the three services that sign/verify quote tokens.
            case "$SVC" in
              trip-service|payment-service|booking-service)
                echo "QUOTE_TOKEN_SECRET=${FINAL_QUOTE_TOKEN_SECRET}" ;;
            esac
```

- [ ] **Step 4: Verify booking-service compiles**

```bash
cd microservices/booking-service && ./mvnw -q clean compile
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java
git add microservices/booking-service/src/main/resources/application.properties
git add .github/workflows/backend-deploy.yml
git commit -m "P1.2: wire trip.exchange consumer queue + QUOTE_TOKEN_SECRET into booking-service"
```

---

### Task 3: LegBookingService + rewritten LegBookingController

**Files:**
- Modify: `microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingResponse.java`
- Create: `microservices/booking-service/src/main/java/com/travel2go/backend/service/LegBookingService.java`
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java`
- Test: `microservices/booking-service/src/test/java/com/travel2go/backend/service/LegBookingServiceTest.java`
- Modify: `microservices/booking-service/src/test/java/com/travel2go/backend/controller/LegBookingControllerTest.java`

**Interfaces:**
- Consumes: `QuoteTokenService.isValid(String token, String legId, long amountPaise) -> boolean` (existing, `platform-security`, package `com.travel2go.backend.service` — same package as the new `LegBookingService`, no import needed); `BookingRepository` (existing).
- Produces: `LegBookingService.createLegBooking(String tripId, String legId, String quoteToken, Long amountPaise, String ownerUserId) -> Booking`, `.getBooking(String legId, String requestingUserId, boolean isAdmin) -> Booking` (throws `IllegalArgumentException` if not found or not authorized — caller maps to `404`) — consumed by `LegBookingController`. `LegBookingResponse{legId, status}` (renamed field) — consumed by trip-service in Task 5.

**IMPORTANT — this task will leave `trip-service` broken.** `TripService.bookLeg()` still calls `response.getBookingId()`, which this task's Step 1 removes from `LegBookingResponse`. That is **expected and correct** — Task 5 fixes it. Do not touch any file under `microservices/trip-service` in this task. Verify only `booking-service` and `common-models` compile; `trip-service` failing to compile at this point is not your concern.

`BookingRepository` needs one new derived-query method for the idempotent-create/read lookup:

- [ ] **Step 1: Rename LegBookingResponse's field and add the repository lookup method**

Replace `microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingResponse.java` with:

```java
package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegBookingResponse {
    private String legId;
    private String status;
}
```

Replace `microservices/booking-service/src/main/java/com/travel2go/backend/repository/BookingRepository.java` with:

```java
package com.travel2go.backend.repository;

import com.travel2go.backend.model.Booking;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface BookingRepository extends FirestoreReactiveRepository<Booking> {
    Flux<Booking> findByLegId(String legId);
}
```

Rebuild `common-models` so the renamed field is visible locally:

```bash
cd microservices/common-models && ./mvnw -q clean install
```

- [ ] **Step 2: Write the failing tests for LegBookingService**

`microservices/booking-service/src/test/java/com/travel2go/backend/service/LegBookingServiceTest.java`:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegBookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private QuoteTokenService quoteTokenService;

    private LegBookingService legBookingService;

    @BeforeEach
    void setUp() {
        legBookingService = new LegBookingService(bookingRepository, quoteTokenService);
        lenient().when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void createLegBooking_validTokenPersistsPendingWithOwner() {
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.empty());
        when(quoteTokenService.isValid("quote-abc", "leg-1", 150000L)).thenReturn(true);

        Booking result = legBookingService.createLegBooking("trip-1", "leg-1", "quote-abc", 150000L, "user-1");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
        assertThat(result.getLegId()).isEqualTo("leg-1");
    }

    @Test
    void createLegBooking_invalidTokenRejectsAndPersistsAuditRecord() {
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.empty());
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        assertThatThrownBy(() ->
                legBookingService.createLegBooking("trip-1", "leg-1", "bad-token", 150000L, "user-1"))
                .isInstanceOf(LegBookingRejectedException.class);

        verify(bookingRepository).save(argThatStatusIs("REJECTED"));
    }

    @Test
    void createLegBooking_existingBookingForLegIdIsIdempotent() {
        Booking existing = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(existing));

        Booking result = legBookingService.createLegBooking("trip-1", "leg-1", "quote-abc", 150000L, "user-1");

        assertThat(result).isSameAs(existing);
        verify(quoteTokenService, never()).isValid(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getBooking_returnsForOwner() {
        Booking booking = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(booking));

        Booking result = legBookingService.getBooking("leg-1", "user-1", false);

        assertThat(result).isSameAs(booking);
    }

    @Test
    void getBooking_throwsForNonOwnerNonAdmin() {
        Booking booking = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(booking));

        assertThatThrownBy(() -> legBookingService.getBooking("leg-1", "user-2", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBooking_allowsAdminForAnyOwner() {
        Booking booking = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(booking));

        Booking result = legBookingService.getBooking("leg-1", "admin-user", true);

        assertThat(result).isSameAs(booking);
    }

    private static Booking argThatStatusIs(String status) {
        return org.mockito.ArgumentMatchers.argThat(b -> status.equals(b.getStatus()));
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
cd microservices/booking-service && ./mvnw -q test -Dtest=LegBookingServiceTest
```
Expected: FAIL — `LegBookingService` and `LegBookingRejectedException` don't exist yet.

- [ ] **Step 4: Implement LegBookingRejectedException and LegBookingService**

`microservices/booking-service/src/main/java/com/travel2go/backend/service/LegBookingRejectedException.java`:

```java
package com.travel2go.backend.service;

public class LegBookingRejectedException extends RuntimeException {
    public LegBookingRejectedException(String message) {
        super(message);
    }
}
```

`microservices/booking-service/src/main/java/com/travel2go/backend/service/LegBookingService.java`:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LegBookingService {

    private final BookingRepository bookingRepository;
    private final QuoteTokenService quoteTokenService;

    public Booking createLegBooking(String tripId, String legId, String quoteToken, Long amountPaise, String ownerUserId) {
        Booking existing = findByLegId(legId);
        if (existing != null) {
            return existing;
        }

        boolean quoteValid = quoteTokenService.isValid(quoteToken, legId, amountPaise);

        if (!quoteValid) {
            Booking rejected = Booking.builder()
                    .tripId(tripId)
                    .legId(legId)
                    .quoteToken(quoteToken)
                    .amountPaise(amountPaise)
                    .feePaise(0L)
                    .ownerUserId(ownerUserId)
                    .status("REJECTED")
                    .bookingDate(new Date())
                    .build();
            bookingRepository.save(rejected).block();
            throw new LegBookingRejectedException("Invalid quote token for legId " + legId);
        }

        Booking booking = Booking.builder()
                .tripId(tripId)
                .legId(legId)
                .quoteToken(quoteToken)
                .amountPaise(amountPaise)
                .feePaise(0L)
                .ownerUserId(ownerUserId)
                .status("PENDING")
                .bookingDate(new Date())
                .build();

        return bookingRepository.save(booking).block();
    }

    public Booking getBooking(String legId, String requestingUserId, boolean isAdmin) {
        Booking booking = findByLegId(legId);
        if (booking == null) {
            throw new IllegalArgumentException("No booking found for legId " + legId);
        }
        if (!isAdmin && !requestingUserId.equals(booking.getOwnerUserId())) {
            throw new IllegalArgumentException("No booking found for legId " + legId);
        }
        return booking;
    }

    private Booking findByLegId(String legId) {
        List<Booking> bookings = bookingRepository.findByLegId(legId).collectList().block();
        if (bookings == null || bookings.isEmpty()) {
            return null;
        }
        return bookings.get(0);
    }
}
```

Note: `getBooking` deliberately throws the same `IllegalArgumentException` with the same message for "not found" and "found but not yours" — this is what makes the controller's `404` response indistinguishable between the two cases (Global Constraints: don't confirm existence to an unauthorized caller).

- [ ] **Step 5: Run tests to confirm they pass**

```bash
cd microservices/booking-service && ./mvnw -q test -Dtest=LegBookingServiceTest
```
Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 6: Rewrite LegBookingController**

Replace `microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java` with:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.service.LegBookingRejectedException;
import com.travel2go.backend.service.LegBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leg-bookings")
@RequiredArgsConstructor
public class LegBookingController {

    private final LegBookingService legBookingService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping
    public ResponseEntity<LegBookingResponse> createLegBooking(@RequestBody LegBookingRequest request) {
        try {
            Booking booking = legBookingService.createLegBooking(
                    request.getTripId(), request.getLegId(), request.getQuoteToken(),
                    request.getAmountPaise(), currentUserId());
            return ResponseEntity.ok(new LegBookingResponse(booking.getLegId(), booking.getStatus()));
        } catch (LegBookingRejectedException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
        }
    }

    @GetMapping("/{legId}")
    public ResponseEntity<Booking> getBooking(@PathVariable String legId) {
        try {
            return ResponseEntity.ok(legBookingService.getBooking(legId, currentUserId(), currentUserIsAdmin()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
```

- [ ] **Step 7: Update the existing controller test**

Replace `microservices/booking-service/src/test/java/com/travel2go/backend/controller/LegBookingControllerTest.java` with:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.service.LegBookingRejectedException;
import com.travel2go.backend.service.LegBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegBookingControllerTest {

    @Mock private LegBookingService legBookingService;

    private LegBookingController controller;

    @BeforeEach
    void setUp() {
        controller = new LegBookingController(legBookingService);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null, java.util.List.of()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void createLegBooking_returnsPendingWithLegIdReference() {
        LegBookingRequest request = LegBookingRequest.builder()
                .tripId("trip-1").legId("leg-1").quoteToken("quote-abc").amountPaise(150000L).build();

        Booking pending = Booking.builder().legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(legBookingService.createLegBooking("trip-1", "leg-1", "quote-abc", 150000L, "user-1"))
                .thenReturn(pending);

        ResponseEntity<LegBookingResponse> response = controller.createLegBooking(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLegId()).isEqualTo("leg-1");
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createLegBooking_rejectedTokenReturns402() {
        LegBookingRequest request = LegBookingRequest.builder()
                .tripId("trip-1").legId("leg-1").quoteToken("bad").amountPaise(150000L).build();

        when(legBookingService.createLegBooking(eq("trip-1"), eq("leg-1"), eq("bad"), anyLong(), eq("user-1")))
                .thenThrow(new LegBookingRejectedException("invalid"));

        ResponseEntity<LegBookingResponse> response = controller.createLegBooking(request);

        assertThat(response.getStatusCode().value()).isEqualTo(402);
    }

    @Test
    void getBooking_returns404WhenServiceRejects() {
        when(legBookingService.getBooking("leg-1", "user-1", false))
                .thenThrow(new IllegalArgumentException("No booking found for legId leg-1"));

        ResponseEntity<Booking> response = controller.getBooking("leg-1");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
```

- [ ] **Step 8: Run the full booking-service suite**

```bash
cd microservices/booking-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
```
Expected: `BUILD SUCCESS`, all tests pass. Do NOT attempt to build `trip-service` here — it is expected to be broken until Task 5.

- [ ] **Step 9: Commit**

```bash
git add microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingResponse.java
git add microservices/booking-service/src/main/java/com/travel2go/backend/repository/BookingRepository.java
git add microservices/booking-service/src/main/java/com/travel2go/backend/service/
git add microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java
git add microservices/booking-service/src/test/java/com/travel2go/backend/service/
git add microservices/booking-service/src/test/java/com/travel2go/backend/controller/LegBookingControllerTest.java
git commit -m "P1.2: independent quote-token validation, PENDING leg bookings, legId as canonical reference"
```

---

### Task 4: booking-service payment.captured consumer

**Files:**
- Create: `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedEvent.java`
- Create: `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java`
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`
- Test: `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`

**Interfaces:**
- Consumes: `BookingRepository.findByLegId(String) -> Flux<Booking>` (Task 3); `NotificationClient.sendBookingConfirmation(NotificationClient.NotificationRequest)` (existing, B5-resilient).
- Produces: nothing consumed by later tasks — this is the terminal consumer for booking-service.

- [ ] **Step 1: Add the local event record**

`microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedEvent.java`:

```java
package com.travel2go.backend.consumer;

public record PaymentCapturedEvent(String bookingRef, String providerPaymentId, long amountPaise) {
}
```

- [ ] **Step 2: Configure the Jackson type mapping so the consumer can deserialize payment-service's message**

In `microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`, replace the `jsonMessageConverter()` bean:

```java
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper typeMapper =
                new org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setIdClassMapping(java.util.Map.of(
                "com.travel2go.backend.service.PaymentCapturedEvent",
                com.travel2go.backend.consumer.PaymentCapturedEvent.class));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
```

This is necessary because `payment-service` publishes its `PaymentCapturedEvent` (package `com.travel2go.backend.service`) via the default `Jackson2JsonMessageConverter`, which stamps a `__TypeId__` header with that fully-qualified name. Without this mapping, booking-service's consumer would try to load a class that doesn't exist in its own classpath and throw on every message.

- [ ] **Step 3: Write the failing consumer tests**

`microservices/booking-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`:

```java
package com.travel2go.backend.consumer;

import com.travel2go.backend.client.NotificationClient;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCapturedConsumerTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationClient notificationClient;

    private PaymentCapturedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentCapturedConsumer(bookingRepository, notificationClient);
        lenient().when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void onPaymentCaptured_confirmsPendingBookingAndNotifiesOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository).save(argThat(b ->
                "CONFIRMED".equals(b.getStatus()) && "pay_1".equals(b.getProviderPaymentId()) && b.getConfirmedAt() != null));
        verify(notificationClient, times(1)).sendBookingConfirmation(any());
    }

    @Test
    void onPaymentCaptured_duplicateDeliveryConfirmsOnlyOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));
        pending.setStatus("CONFIRMED");
        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(notificationClient, times(1)).sendBookingConfirmation(any());
    }

    @Test
    void onPaymentCaptured_amountMismatchDoesNotConfirm() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 999L));

        verify(bookingRepository, never()).save(any());
        verify(notificationClient, never()).sendBookingConfirmation(any());
    }

    @Test
    void onPaymentCaptured_unknownLegIdDoesNotThrow() {
        when(bookingRepository.findByLegId("leg-unknown")).thenReturn(Flux.empty());

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-unknown", "pay_1", 150000L));

        verify(bookingRepository, never()).save(any());
    }
}
```

- [ ] **Step 4: Run the tests to confirm they fail**

```bash
cd microservices/booking-service && ./mvnw -q test -Dtest=PaymentCapturedConsumerTest
```
Expected: FAIL — `PaymentCapturedConsumer` doesn't exist yet.

- [ ] **Step 5: Implement the consumer**

`microservices/booking-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java`:

```java
package com.travel2go.backend.consumer;

import com.travel2go.backend.client.NotificationClient;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
        Booking booking = findByLegId(event.bookingRef());

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

    private Booking findByLegId(String legId) {
        List<Booking> bookings = bookingRepository.findByLegId(legId).collectList().block();
        if (bookings == null || bookings.isEmpty()) {
            return null;
        }
        return bookings.get(0);
    }
}
```

- [ ] **Step 6: Run the tests to confirm they pass**

```bash
cd microservices/booking-service && ./mvnw -q test -Dtest=PaymentCapturedConsumerTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 7: Run the full booking-service suite**

```bash
cd microservices/booking-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add microservices/booking-service/src/main/java/com/travel2go/backend/consumer/
git add microservices/booking-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java
git add microservices/booking-service/src/test/java/com/travel2go/backend/consumer/
git commit -m "P1.2: add booking-service payment.captured consumer (idempotent confirm + notify)"
```

---

### Task 5: trip-service bookLeg() fix

**Files:**
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`
- Modify: `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java`

**Interfaces:**
- Consumes: `LegBookingResponse.getLegId()` (renamed in Task 3).
- Produces: `Leg.status="PENDING"` after `bookLeg()` (was `"CONFIRMED"`) — this is what Task 6's consumer flips to `CONFIRMED`.

This is the task that fixes the compile break Task 3 left in trip-service. Rebuild `common-models` first if you haven't already picked up Task 3's `LegBookingResponse` rename.

- [ ] **Step 1: Rebuild common-models locally to pick up the renamed field**

```bash
cd microservices/common-models && ./mvnw -q clean install
cd ../trip-service && ./mvnw -q clean compile 2>&1 | tail -20
```
Expected: FAIL — `TripService.java` references `response.getBookingId()`, which no longer exists (`getLegId()` does). This confirms the expected break from Task 3; fix it in the next step.

- [ ] **Step 2: Update the failing test's expectations first**

In `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java`, replace the `bookLeg_confirmsLegAndPublishesEvent` test:

```java
    @Test
    void bookLeg_setsLegPendingAndPublishesEvent() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").build();
        com.travel2go.backend.model.Leg leg = com.travel2go.backend.model.Leg.builder()
                .id("leg-1").tripId("trip-1").status("SELECTED")
                .pricePaise(150000L).quoteToken("quote-token-abc").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(quoteTokenService.isValid("quote-token-abc", "leg-1", 150000L)).thenReturn(true);
        when(bookingClient.createLegBooking(any())).thenReturn(
                com.travel2go.backend.dto.LegBookingResponse.builder()
                        .legId("leg-1").status("PENDING").build());
        when(legRepository.save(any(com.travel2go.backend.model.Leg.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        com.travel2go.backend.model.Leg result = tripService.bookLeg("trip-1", "leg-1", "user-1");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getSupplierRef()).isEqualTo("leg-1");
        verify(eventPublisher).publish(eq("leg.booked"), any());

        org.mockito.ArgumentCaptor<com.travel2go.backend.dto.LegBookingRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.travel2go.backend.dto.LegBookingRequest.class);
        verify(bookingClient).createLegBooking(captor.capture());
        assertThat(captor.getValue().getAmountPaise()).isEqualTo(150000L);
        assertThat(captor.getValue().getQuoteToken()).isEqualTo("quote-token-abc");
    }
```

(This replaces the old `bookLeg_confirmsLegAndPublishesEvent` test by name and content — delete the old one, this is its replacement. The other `bookLeg_*` tests in this file — `bookLeg_rejectsLegFromDifferentTrip`, `bookLeg_rejectsNonOwner_withoutCallingBookingClient`, `bookLeg_rejectsInvalidQuoteToken_withoutCallingBookingClient` — are unaffected and stay as-is.)

- [ ] **Step 3: Run the test to confirm it fails**

```bash
cd microservices/trip-service && ./mvnw -q test -Dtest=TripServiceTest#bookLeg_setsLegPendingAndPublishesEvent
```
Expected: FAIL — compile error, `TripService.java` still uses the old field/status.

- [ ] **Step 4: Fix TripService.bookLeg()**

In `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`, replace:

```java
        leg.setStatus("CONFIRMED");
        leg.setSupplierRef(response.getBookingId());
        Leg saved = legRepository.save(leg).block();

        eventPublisher.publish("leg.booked", Map.of(
                "tripId", tripId,
                "legId", legId,
                "bookingId", response.getBookingId()
        ));
```

with:

```java
        leg.setStatus("PENDING");
        leg.setSupplierRef(response.getLegId());
        Leg saved = legRepository.save(leg).block();

        eventPublisher.publish("leg.booked", Map.of(
                "tripId", tripId,
                "legId", legId,
                "bookingId", response.getLegId()
        ));
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
cd microservices/trip-service && ./mvnw -q test -Dtest=TripServiceTest
```
Expected: `Tests run: 10, Failures: 0, Errors: 0` (all of TripServiceTest, not just the one method).

- [ ] **Step 6: Run the full trip-service suite**

```bash
cd microservices/trip-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java
git add microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java
git commit -m "P1.2: trip-service bookLeg sets Leg PENDING (not CONFIRMED), uses legId reference"
```

---

### Task 6: trip-service payment.captured consumer

**Files:**
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedEvent.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java`
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`
- Test: `microservices/trip-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`

**Interfaces:**
- Consumes: `LegRepository` (existing — check `microservices/trip-service/src/main/java/com/travel2go/backend/repository/LegRepository.java` for its exact existing methods; it already has `findByTripId`, this task needs `findById` which `FirestoreReactiveRepository` already provides).
- Produces: nothing consumed by later tasks — terminal consumer for trip-service.

`Leg` documents are keyed by their own Firestore doc-id, which **is** the `legId` used throughout this plan (confirmed: `TripService.bookLeg(tripId, legId, ...)` calls `legRepository.findById(legId)` directly — the `Leg`'s doc-id and "legId" are the same value everywhere in the existing code). So this consumer looks up the `Leg` via the repository's inherited `findById`, not a custom derived-query method.

- [ ] **Step 1: Add the local event record**

`microservices/trip-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedEvent.java`:

```java
package com.travel2go.backend.consumer;

public record PaymentCapturedEvent(String bookingRef, String providerPaymentId, long amountPaise) {
}
```

- [ ] **Step 2: Declare the new queue and configure Jackson type mapping**

Replace `microservices/trip-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java` with:

```java
package com.travel2go.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Autowired
    public void configureVirtualHost(ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory) {
            CachingConnectionFactory cachingFactory = (CachingConnectionFactory) connectionFactory;
            if ("/".equals(cachingFactory.getVirtualHost()) && !"guest".equals(cachingFactory.getUsername())) {
                cachingFactory.setVirtualHost(cachingFactory.getUsername());
            }
        }
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setIdClassMapping(Map.of(
                "com.travel2go.backend.service.PaymentCapturedEvent",
                com.travel2go.backend.consumer.PaymentCapturedEvent.class));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }

    @Bean
    public Queue tripPaymentCapturedQueue() {
        return new Queue("trip.payment-captured", true);
    }

    @Bean
    public Binding tripPaymentCapturedBinding() {
        return BindingBuilder.bind(tripPaymentCapturedQueue()).to(tripExchange()).with("payment.captured");
    }
}
```

- [ ] **Step 3: Write the failing consumer tests**

`microservices/trip-service/src/test/java/com/travel2go/backend/consumer/PaymentCapturedConsumerTest.java`:

```java
package com.travel2go.backend.consumer;

import com.travel2go.backend.model.Leg;
import com.travel2go.backend.repository.LegRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCapturedConsumerTest {

    @Mock private LegRepository legRepository;

    private PaymentCapturedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentCapturedConsumer(legRepository);
        lenient().when(legRepository.save(any(Leg.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void onPaymentCaptured_confirmsPendingLeg() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(legRepository).save(argThat(l -> "CONFIRMED".equals(l.getStatus())));
    }

    @Test
    void onPaymentCaptured_duplicateDeliveryIsNoOp() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));
        leg.setStatus("CONFIRMED");
        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(legRepository, times(1)).save(any());
    }

    @Test
    void onPaymentCaptured_unknownLegDoesNotThrow() {
        when(legRepository.findById("leg-unknown")).thenReturn(Mono.empty());

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-unknown", "pay_1", 150000L));

        verify(legRepository, never()).save(any());
    }
}
```

- [ ] **Step 4: Run the tests to confirm they fail**

```bash
cd microservices/trip-service && ./mvnw -q test -Dtest=PaymentCapturedConsumerTest
```
Expected: FAIL — `PaymentCapturedConsumer` doesn't exist yet.

- [ ] **Step 5: Implement the consumer**

`microservices/trip-service/src/main/java/com/travel2go/backend/consumer/PaymentCapturedConsumer.java`:

```java
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
 * one does not send a notification, since booking-service's consumer already
 * does. Idempotent via a status-guard, same pattern as booking-service's
 * consumer and payment-service's own (P1.1).
 *
 * An event for a legId with no matching Leg (shouldn't happen) is logged at
 * ERROR and acked, not requeued - see booking-service's PaymentCapturedConsumer
 * for the same reasoning.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCapturedConsumer {

    private final LegRepository legRepository;

    @RabbitListener(queues = "trip.payment-captured")
    public void onPaymentCaptured(PaymentCapturedEvent event) {
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

        leg.setStatus("CONFIRMED");
        legRepository.save(leg).block();
    }
}
```

- [ ] **Step 6: Run the tests to confirm they pass**

```bash
cd microservices/trip-service && ./mvnw -q test -Dtest=PaymentCapturedConsumerTest
```
Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 7: Run the full trip-service suite**

```bash
cd microservices/trip-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add microservices/trip-service/src/main/java/com/travel2go/backend/consumer/
git add microservices/trip-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java
git add microservices/trip-service/src/test/java/com/travel2go/backend/consumer/
git commit -m "P1.2: add trip-service payment.captured consumer (flips Leg PENDING to CONFIRMED)"
```

---

### Task 7: payment-service ownerUserId (closes the P1.1-review IDOR)

**Files:**
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `Payment.getOwnerUserId()/.setOwnerUserId(String)` (Task 1).
- Produces: `PaymentService.createOrder(..., String ownerUserId)` (signature change — new trailing parameter), `.getStatus(String bookingRef, String requestingUserId, boolean isAdmin)` (signature change).

This mirrors Task 3's ownership pattern exactly (`404` for both missing and non-owner, `ROLE_ADMIN` bypass, controller extracts from `SecurityContextHolder` and passes plain values into the service for testability).

- [ ] **Step 1: Write the failing tests**

In `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`, update the `setUp()` and add new test cases. First, the `createOrder` calls throughout the file gain a trailing `"user-1"` argument — update every existing call site:

```java
paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1")
```

(apply this same trailing-argument change to every `paymentService.createOrder(...)` call already in the file — there are several, in `createOrder_succeedsAndNeverAddsAFee`, `createOrder_rejectsWhenQuoteTokenInvalid_withoutCallingProvider`, and any webhook/refund test that creates a payment via `createOrder` as setup).

Add these new test methods:

```java
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
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(
                Flux.just(Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        Payment result = paymentService.getStatus("leg-1", "user-1", false);

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void getStatus_throwsForNonOwnerNonAdmin() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(
                Flux.just(Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        assertThatThrownBy(() -> paymentService.getStatus("leg-1", "user-2", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getStatus_allowsAdminForAnyOwner() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(
                Flux.just(Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        Payment result = paymentService.getStatus("leg-1", "admin-user", true);

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }
```

Add `import static org.assertj.core.api.Assertions.assertThatThrownBy;` and `import java.util.Date;` to the test file's imports if not already present.

Note: `refund(bookingRef)` internally calls `getStatus(bookingRef, ...)` — after this task's Step 3, `refund`'s internal call must pass an owner/admin bypass (it's an admin-only endpoint already, per `SecurityConfig`'s `hasAuthority("ROLE_ADMIN")` on that route) — see Step 3 for how `refund` adapts.

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentServiceTest
```
Expected: FAIL — compile error, `createOrder`/`getStatus` don't have the new parameters yet.

- [ ] **Step 3: Update PaymentService**

In `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`:

Replace the `createOrder` method signature and body's `Payment.builder()` calls to set `ownerUserId`:

```java
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
```

Replace `getStatus`:

```java
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
```

Update `refund` to call `getStatus` with an admin bypass (the route is already `ROLE_ADMIN`-only at `SecurityConfig`, so `refund`'s internal ownership check should not re-block the admin caller from refunding any bookingRef):

```java
    public Payment refund(String bookingRef) {
        Payment payment = getStatus(bookingRef, bookingRef, true);
```

(the `requestingUserId` argument here is irrelevant when `isAdmin=true`, since the ownership branch is skipped entirely — passing `bookingRef` itself is just a harmless placeholder, not used for anything).

- [ ] **Step 4: Run tests to confirm they pass**

```bash
cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentServiceTest
```
Expected: all tests pass (existing 9 + 4 new = 13, plus whatever this file already grew to across earlier P1.1 fix-pass work — check the actual count in the output, just confirm `Failures: 0, Errors: 0`).

- [ ] **Step 5: Update PaymentController**

In `microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java`, add the same `currentUserId()`/`currentUserIsAdmin()` helpers as booking-service's `LegBookingController` (Task 3), and wire them into `createOrder` and `getStatus`:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.dto.CreateOrderRequest;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.InvalidWebhookSignatureException;
import com.travel2go.backend.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping("/order")
    public ResponseEntity<Payment> createOrder(@RequestBody CreateOrderRequest request) {
        if (request.getAmountPaise() == null) {
            return ResponseEntity.badRequest().build();
        }
        Payment payment = paymentService.createOrder(
                request.getBookingRef(), request.getAmountPaise(), request.getMethod(), request.getQuoteToken(),
                currentUserId());

        if ("REJECTED".equals(payment.getStatus())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(payment);
        }
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] rawBody, HttpServletRequest request) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        try {
            paymentService.applyWebhook(rawBody, headers);
            return ResponseEntity.ok().build();
        } catch (InvalidWebhookSignatureException | UncheckedIOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{bookingRef}")
    public ResponseEntity<Payment> getStatus(@PathVariable String bookingRef) {
        try {
            return ResponseEntity.ok(paymentService.getStatus(bookingRef, currentUserId(), currentUserIsAdmin()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{bookingRef}/refund")
    public ResponseEntity<Payment> refund(@PathVariable String bookingRef) {
        return ResponseEntity.ok(paymentService.refund(bookingRef));
    }
}
```

Note: `/webhook` is `permitAll()` and never reaches `currentUserId()`/`currentUserIsAdmin()` — unaffected. `/order` and `/{bookingRef}` both require an authenticated `SecurityContext` per the existing `SecurityConfig` (`anyRequest().authenticated()` catches both), so `currentUserId()` is always safe to call there.

There is an existing `PaymentControllerWebhookTest` (`@WebMvcTest`) — it only exercises the `/webhook` path (`permitAll`), which this change doesn't touch, so it should be unaffected. Run it to confirm:

```bash
cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentControllerWebhookTest
```
Expected: still passes unchanged.

- [ ] **Step 6: Run the full payment-service suite**

```bash
cd microservices/payment-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java
git add microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java
git add microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java
git commit -m "P1.2: capture ownerUserId on createOrder, enforce it on getStatus (closes P1.1-review IDOR)"
```

---

## After all tasks: final verification

Run the brief's own §9 verification commands and paste the output as part of hand-back:

```bash
# no confirm-on-create on the leg path
grep -n 'status("CONFIRMED")\|status(.CONFIRMED.)' microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java microservices/booking-service/src/main/java/com/travel2go/backend/service/*.java 2>/dev/null && echo "REVIEW: confirm-on-create still present" || echo "OK: no confirm-on-create"

# independent quote-token validation present in booking
grep -rn 'QuoteTokenService\|isValid(' microservices/booking-service/src/main --include='*.java' | grep -v -i test && echo "validation present"

# consumer + idempotency present
grep -rn 'RabbitListener\|payment.captured' microservices/booking-service/src/main --include='*.java'
grep -rn 'RabbitListener\|payment.captured' microservices/trip-service/src/main --include='*.java'

# quote secret wired + scoped
grep -n 'quote.token.secret' microservices/booking-service/src/main/resources/application.properties
grep -n 'QUOTE_TOKEN_SECRET' .github/workflows/backend-deploy.yml

# ownership on payment getStatus and booking reads
grep -rn 'ownerUserId' microservices/payment-service/src/main microservices/booking-service/src/main --include='*.java'

# full suite, all three touched services
cd microservices/booking-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
cd ../trip-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
cd ../payment-service && QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" ./mvnw -q clean test
```

Then hand back per the brief §11: files touched; the `LegBookingService` validation order; the queue/binding names in both new consumers and how their idempotency works (the status-guard rule, and the deliberate log-and-ack-not-requeue choice for an unknown legId, which is a documented simplification of the brief's literal "nack-with-bounded-requeue or dead-letter" language — no DLX/retry infra existed in the codebase to build on); the identifier-consistency decision (`legId` end-to-end, including the trip-service scope expansion the user approved); how `ownerUserId` is captured/enforced in both services (and the `404`-for-both choice over `403`); the §9 output; and a reminder that the P1.1 idempotency race is mitigated on the consumer side here but fully closed only when P1.3 adds a DB unique constraint.
