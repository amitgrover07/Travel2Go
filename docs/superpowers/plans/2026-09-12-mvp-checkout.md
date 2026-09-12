# MVP Checkout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing money loop (P1.1–P1.4) reachable end-to-end from a real browser: sign quote tokens server-side, route the payment API through the gateway, send a real leg-booking confirmation, and add the frontend checkout flow that drives all of it.

**Architecture:** Three small additive backend completions (trip-service signs the token; api-gateway gains a route; booking-service sends a real notification via a new notification-service endpoint) plus one new frontend page that calls the now-complete backend chain and never trusts the client-side Razorpay callback — it polls payment-service's own status until the webhook has actually captured the payment.

**Tech Stack:** Spring Boot (existing), React 19 + react-router-dom 7 + axios (existing), Razorpay Checkout.js (new, loaded via script tag, no npm package).

## Global Constraints

- `bookingRef == legId` everywhere — never introduce a different reference.
- The quote token's subject is always the server-generated `legId`; any client-supplied `quoteToken` on a request is accepted into the DTO for wire compatibility but never trusted/used.
- Razorpay `key_id` is public (frontend `VITE_RAZORPAY_KEY_ID`); `key_secret` and `webhook_secret` are payment-service-only, never sent to the frontend.
- Truth of payment is the webhook, surfaced via `GET /api/payments/{legId}` — the frontend must never confirm from the Razorpay client-side callback alone.
- No existing endpoint, model, consumer, or test is deleted or rewritten beyond what each task below names — additive only.
- G1 (`fee_paise`/`feePaise` always 0), G2 (quote-token gate, now genuinely enforced), payment-gated confirmation, owner scoping, and RabbitMQ `trustedPackages` all stay intact — no task here touches any of that machinery.
- Frontend verification is manual (dev server + browser) — no new test framework is introduced.

---

### Task 1: trip-service signs the quote token server-side

**Files:**
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`
- Modify: `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java`

**Interfaces:**
- Consumes: `QuoteTokenService.issue(String legId, long pricePaise)` → `String` (already exists in `platform-security`, already injected into `TripService` as `quoteTokenService`).
- Produces: `TripService.addLeg(...)` now returns a `Leg` whose `quoteToken` is always server-signed — later tasks must not reintroduce trust in `AddLegRequest.getQuoteToken()`.

- [ ] **Step 1: Write the failing test**

Add this test to `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java` (add it near `addLeg_appendsLegIdToTripAndSavesLeg`):

```java
    @Test
    void addLeg_signsQuoteTokenServerSide_ignoringClientSuppliedToken() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").legIds(new java.util.ArrayList<>()).build();
        AddLegRequest request = AddLegRequest.builder()
                .type("RAIL")
                .pricePaise(150000L)
                .quoteToken("client-supplied-should-be-ignored")
                .build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.save(any(Leg.class))).thenAnswer(inv -> {
            Leg l = inv.getArgument(0);
            l.setId("leg-1");
            return Mono.just(l);
        });
        when(quoteTokenService.issue("leg-1", 150000L)).thenReturn("server-signed-token");
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Leg result = tripService.addLeg("trip-1", request, "user-1");

        assertThat(result.getQuoteToken()).isEqualTo("server-signed-token");
        verify(quoteTokenService).issue("leg-1", 150000L);
    }
```

Also update the existing `addLeg_appendsLegIdToTripAndSavesLeg` test to stub the new call (it will otherwise throw an unstubbed-mock exception under strict Mockito settings once `addLeg` calls `issue`):

```java
    @Test
    void addLeg_appendsLegIdToTripAndSavesLeg() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").legIds(new java.util.ArrayList<>()).build();
        AddLegRequest request = AddLegRequest.builder()
                .type("RAIL")
                .pricePaise(150000L)
                .quoteToken("quote-abc")
                .build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.save(any(Leg.class))).thenAnswer(inv -> {
            Leg l = inv.getArgument(0);
            l.setId("leg-1");
            return Mono.just(l);
        });
        when(quoteTokenService.issue("leg-1", 150000L)).thenReturn("server-signed-token");
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Leg result = tripService.addLeg("trip-1", request, "user-1");

        assertThat(result.getId()).isEqualTo("leg-1");
        assertThat(result.getStatus()).isEqualTo("SELECTED");
        assertThat(trip.getLegIds()).containsExactly("leg-1");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd microservices/trip-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" BOOKING_SERVICE_URL="http://localhost:8080" ./mvnw test -Dtest=TripServiceTest`
Expected: FAIL — `addLeg_signsQuoteTokenServerSide_ignoringClientSuppliedToken` fails because `addLeg` never calls `quoteTokenService.issue`, so `result.getQuoteToken()` still equals the client-supplied value.

- [ ] **Step 3: Rewrite `addLeg`**

In `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`, replace the `addLeg` method body:

```java
    public Leg addLeg(String tripId, AddLegRequest request, String requestingUserId) {
        Trip trip = tripRepository.findById(tripId).block();
        if (trip == null) {
            throw new IllegalArgumentException("Trip not found: " + tripId);
        }
        assertOwner(trip, requestingUserId);

        Leg leg = Leg.builder()
                .tripId(tripId)
                .type(request.getType())
                .status("SELECTED")
                .pricePaise(request.getPricePaise())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .metadata(request.getMetadata())
                .build();

        Leg savedLeg = legRepository.save(leg).block();

        String signedToken = quoteTokenService.issue(savedLeg.getId(), savedLeg.getPricePaise());
        savedLeg.setQuoteToken(signedToken);
        savedLeg = legRepository.save(savedLeg).block();

        trip.getLegIds().add(savedLeg.getId());
        trip.setUpdatedAt(new Date());
        tripRepository.save(trip).block();

        return savedLeg;
    }
```

Note what changed: the `Leg.builder()` call no longer sets `.quoteToken(request.getQuoteToken())` at all — any client-supplied token is now completely ignored (never read from the request). The server always issues its own token, right after the `legId` exists, then persists it with a second save.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd microservices/trip-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" BOOKING_SERVICE_URL="http://localhost:8080" ./mvnw test -Dtest=TripServiceTest`
Expected: PASS, all tests including the new one.

- [ ] **Step 5: Run the full trip-service suite**

Run: `cd microservices/trip-service && DOCKER_HOST=tcp://localhost:2375 JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" BOOKING_SERVICE_URL="http://localhost:8080" ./mvnw clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java
git commit -m "MVP 1A: sign the quote token server-side in addLeg, ignoring any client-supplied token"
```

---

### Task 2: api-gateway routes the payment API

**Files:**
- Modify: `microservices/api-gateway/src/main/resources/application.properties`

**Interfaces:**
- Consumes: `PAYMENT_SERVICE_URL` (already required and validated at startup by `DownstreamUrlValidator` — no change needed there).
- Produces: `/api/payments/**` now reaches payment-service through the gateway (order/status/refund), in addition to the existing `/api/payments/webhook` route.

- [ ] **Step 1: Add the new route and correct the stale comment**

In `microservices/api-gateway/src/main/resources/application.properties`, replace lines 47–55 (the `payment-webhook` route and its trailing comment block) with:

```properties
spring.cloud.gateway.routes[9].id=payment-webhook
spring.cloud.gateway.routes[9].uri=${PAYMENT_SERVICE_URL}
spring.cloud.gateway.routes[9].predicates[0]=Path=/api/payments/webhook

spring.cloud.gateway.routes[10].id=payment-service
spring.cloud.gateway.routes[10].uri=${PAYMENT_SERVICE_URL}
spring.cloud.gateway.routes[10].predicates[0]=Path=/api/payments/**

# payment-service is otherwise internal-only (--no-allow-unauthenticated, see
# A4 / infra/terraform/run-invoker.tf). routes[9] fronts only the exact webhook
# path so Razorpay's callback (which carries no GCP identity) can reach it;
# routes[10] fronts the rest of the payment API (order/status/refund) for the
# frontend, unchanged auth model - order/status require a JWT, refund is
# ROLE_ADMIN, both enforced by payment-service's own SecurityConfig, not by
# the gateway.
```

- [ ] **Step 2: Verify the gateway still boots**

Run: `cd microservices/api-gateway && IDENTITY_SERVICE_URL="http://test-identity" PACKAGE_SERVICE_URL="http://test-package" BOOKING_SERVICE_URL="http://test-booking" MEDIA_SERVICE_URL="http://test-media" TRIP_SERVICE_URL="http://test-trip" PAYMENT_SERVICE_URL="http://test-payment" ./mvnw clean verify`
Expected: BUILD SUCCESS — this confirms `ApiGatewayApplicationTests`' existing context-load test still passes with the new route property present, and `DownstreamUrlValidator` doesn't reject anything (it already required `PAYMENT_SERVICE_URL`, unchanged).

- [ ] **Step 3: Manual verification note (no automated route-level test exists in this codebase for any of the other 9 routes)**

Record in your task report: once deployed (or run locally with a real `PAYMENT_SERVICE_URL`), `curl -X POST http://<gateway-host>/api/payments/order -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" -d '{...}'` should reach payment-service's `/api/payments/order` and return whatever payment-service itself would return (401 without a JWT, a real response with one) — this is a config-only change with no new Java code, so there is nothing further to unit-test.

- [ ] **Step 4: Commit**

```bash
git add microservices/api-gateway/src/main/resources/application.properties
git commit -m "MVP 1B: route /api/payments/** through the gateway to payment-service"
```

---

### Task 3: trip-service threads traveller contact info through `bookLeg`

**Files:**
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/dto/BookLegRequest.java`
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java`
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`
- Modify: `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java`

**Interfaces:**
- Produces: `BookLegRequest(String email, String phone)` — the new request body for `POST /api/trips/{id}/legs/{legId}/book`.
- Produces: `TripService.bookLeg(String tripId, String legId, String requestingUserId, String email, String phone)` — new signature (was 3 params, now 5). Later tasks (frontend) call this endpoint with `{email, phone}` in the body.
- Consumed by Task 4: `com.travel2go.backend.dto.LegBookingRequest` (common-models) gains `email`/`phone` fields that this task's `bookLeg` populates when building the request to `bookingClient.createLegBooking(...)`.

- [ ] **Step 1: Create `BookLegRequest`**

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
public class BookLegRequest {
    private String email;
    private String phone;
}
```

- [ ] **Step 2: Write the failing test**

Add to `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java`:

```java
    @Test
    void bookLeg_threadsEmailAndPhoneIntoLegBookingRequest() {
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

        tripService.bookLeg("trip-1", "leg-1", "user-1", "traveller@example.com", "+911234567890");

        org.mockito.ArgumentCaptor<com.travel2go.backend.dto.LegBookingRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.travel2go.backend.dto.LegBookingRequest.class);
        verify(bookingClient).createLegBooking(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("traveller@example.com");
        assertThat(captor.getValue().getPhone()).isEqualTo("+911234567890");
    }
```

Update the existing `bookLeg_*` tests' calls to `tripService.bookLeg(...)` to pass two more arguments (`null, null` — email/phone aren't the focus of these tests):

- `bookLeg_setsLegPendingAndPublishesEvent`: change `tripService.bookLeg("trip-1", "leg-1", "user-1")` to `tripService.bookLeg("trip-1", "leg-1", "user-1", null, null)`.
- `bookLeg_rejectsLegFromDifferentTrip`: change `() -> tripService.bookLeg("trip-1", "leg-1", "user-1")` to `() -> tripService.bookLeg("trip-1", "leg-1", "user-1", null, null)`.
- `bookLeg_rejectsNonOwner_withoutCallingBookingClient`: change `() -> tripService.bookLeg("trip-1", "leg-1", "user-2")` to `() -> tripService.bookLeg("trip-1", "leg-1", "user-2", null, null)`.
- `bookLeg_rejectsInvalidQuoteToken_withoutCallingBookingClient`: change `() -> tripService.bookLeg("trip-1", "leg-1", "user-1")` to `() -> tripService.bookLeg("trip-1", "leg-1", "user-1", null, null)`.

- [ ] **Step 3: Run test to verify it fails**

Run: `cd microservices/trip-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" BOOKING_SERVICE_URL="http://localhost:8080" ./mvnw test -Dtest=TripServiceTest`
Expected: FAIL — compile error, `bookLeg` doesn't accept 5 arguments yet.

- [ ] **Step 4: Update `TripService.bookLeg`**

Replace the `bookLeg` method in `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`:

```java
    public Leg bookLeg(String tripId, String legId, String requestingUserId, String email, String phone) {
        Trip trip = tripRepository.findById(tripId).block();
        if (trip == null) {
            throw new IllegalArgumentException("Trip not found: " + tripId);
        }
        assertOwner(trip, requestingUserId);

        Leg leg = legRepository.findById(legId).block();
        if (leg == null || !tripId.equals(leg.getTripId())) {
            throw new IllegalArgumentException("Leg not found for trip: " + legId);
        }

        Long pricePaise = leg.getPricePaise();
        String quoteToken = leg.getQuoteToken();
        if (pricePaise == null || quoteToken == null || !quoteTokenService.isValid(quoteToken, legId, pricePaise)) {
            throw new IllegalStateException("Quote token invalid or expired for leg: " + legId);
        }

        com.travel2go.backend.dto.LegBookingResponse response = bookingClient.createLegBooking(
                com.travel2go.backend.dto.LegBookingRequest.builder()
                        .tripId(tripId)
                        .legId(legId)
                        .quoteToken(quoteToken)
                        .amountPaise(pricePaise)
                        .email(email)
                        .phone(phone)
                        .build());

        leg.setStatus("PENDING");
        leg.setSupplierRef(response.getLegId());
        Leg saved = legRepository.save(leg).block();

        eventPublisher.publish("leg.booked", Map.of(
                "tripId", tripId,
                "legId", legId,
                "bookingId", response.getLegId()
        ));

        return saved;
    }
```

- [ ] **Step 5: Update `TripController.bookLeg`**

Replace the `bookLeg` handler in `microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java`:

```java
    @PostMapping("/{id}/legs/{legId}/book")
    public ResponseEntity<Leg> bookLeg(@PathVariable String id, @PathVariable String legId,
            @RequestBody(required = false) BookLegRequest request) {
        String email = request != null ? request.getEmail() : null;
        String phone = request != null ? request.getPhone() : null;
        return ResponseEntity.ok(tripService.bookLeg(id, legId, currentUserId(), email, phone));
    }
```

Add the import `import com.travel2go.backend.dto.BookLegRequest;` to this file. `@RequestBody(required = false)` keeps any existing caller that sends no body at all working (backward-compatible, per the additive-only guardrail).

- [ ] **Step 6: Run tests to verify they pass — expect this to fail to compile until Task 4 adds `email`/`phone` to `LegBookingRequest`**

Run: `cd microservices/trip-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" BOOKING_SERVICE_URL="http://localhost:8080" ./mvnw test -Dtest=TripServiceTest`
Expected: FAIL with a compile error — `LegBookingRequest.builder()...email(...)`/`.phone(...)` don't exist yet on `common-models`' `LegBookingRequest`, since this task's `.email(email)`/`.phone(phone)` builder calls reference fields Task 4 hasn't added. **This is expected and correctly sequenced** — Task 3 cannot fully compile/pass in isolation because trip-service depends on `common-models`' `LegBookingRequest` shape, which Task 4 changes. If you are executing tasks in strict isolation (a fresh subagent per task with only its own commit built on top), report this compile failure as expected in your task report rather than treating it as a defect — the plan sequences Task 4 immediately after for exactly this reason. If you have visibility that Task 4's `common-models` change is already available (e.g., you're told it's already merged), rebuild `common-models` first (`cd ../common-models && ./mvnw -q clean install`) and this will pass cleanly.

- [ ] **Step 7: Commit**

```bash
git add microservices/trip-service/src/main/java/com/travel2go/backend/dto/BookLegRequest.java microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java
git commit -m "MVP: thread traveller email/phone through TripService.bookLeg into LegBookingRequest"
```

---

### Task 4: `LegBookingRequest` gains email/phone; booking-service persists them

**Files:**
- Modify: `microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingRequest.java`
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/service/LegBookingService.java`
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java`
- Modify: `microservices/booking-service/src/test/java/com/travel2go/backend/service/LegBookingServiceTest.java`

**Interfaces:**
- Produces: `LegBookingRequest` now has `email`/`phone` (String, both optional/nullable) alongside the existing `tripId`/`legId`/`quoteToken`/`amountPaise`. This satisfies Task 3's compile dependency.
- Produces: `LegBookingService.createLegBooking(String tripId, String legId, String quoteToken, Long amountPaise, String ownerUserId, String email, String phone)` — new signature (was 5 params, now 7).
- Consumed by Task 5: `Booking.getEmail()`/`getPhone()` (already existing fields on the `Booking` model, now actually populated for leg bookings) are read by `LegConfirmedConsumer` when building the notification payload.

- [ ] **Step 1: Add `email`/`phone` to `LegBookingRequest`**

Replace the full content of `microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingRequest.java`:

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
public class LegBookingRequest {
    private String tripId;
    private String legId;
    private String quoteToken;
    private Long amountPaise;
    private String email;
    private String phone;
}
```

- [ ] **Step 2: Rebuild `common-models` so `booking-service` and `trip-service` see the new fields**

Run: `cd microservices/common-models && ./mvnw -q clean install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Write the failing test**

Add to `microservices/booking-service/src/test/java/com/travel2go/backend/service/LegBookingServiceTest.java`:

```java
    @Test
    void createLegBooking_persistsEmailAndPhoneOnPendingBooking() {
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.empty());
        when(quoteTokenService.isValid("quote-abc", "leg-1", 150000L)).thenReturn(true);

        Booking result = legBookingService.createLegBooking(
                "trip-1", "leg-1", "quote-abc", 150000L, "user-1", "traveller@example.com", "+911234567890");

        assertThat(result.getEmail()).isEqualTo("traveller@example.com");
        assertThat(result.getPhone()).isEqualTo("+911234567890");
    }

    @Test
    void createLegBooking_persistsEmailAndPhoneOnRejectedBooking() {
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.empty());
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        assertThatThrownBy(() -> legBookingService.createLegBooking(
                "trip-1", "leg-1", "bad-token", 150000L, "user-1", "traveller@example.com", "+911234567890"))
                .isInstanceOf(LegBookingRejectedException.class);

        verify(bookingRepository).save(org.mockito.ArgumentMatchers.argThat(b ->
                "REJECTED".equals(b.getStatus())
                        && "traveller@example.com".equals(b.getEmail())
                        && "+911234567890".equals(b.getPhone())));
    }
```

Update every existing call to `legBookingService.createLegBooking(...)` in this file to pass two more trailing arguments, `null, null` (email/phone aren't the focus of those tests):

- `createLegBooking_validTokenPersistsPendingWithOwner`
- `createLegBooking_invalidTokenRejectsAndPersistsAuditRecord`
- `createLegBooking_existingBookingForLegIdIsIdempotent`
- `createLegBooking_samePendingOwnerIsIdempotent`
- `createLegBooking_differentOwnerPendingThrowsConflictAndDoesNotPersist`
- `createLegBooking_legacyActiveBookingWithNullOwnerDoesNotThrowNpe`
- `createLegBooking_rejectedExistingAllowsFreshRetry`

Each becomes e.g. `legBookingService.createLegBooking("trip-1", "leg-1", "quote-abc", 150000L, "user-1", null, null)` (same pattern, two trailing `null`s added to every existing call site in this file).

- [ ] **Step 4: Run test to verify it fails**

Run: `cd microservices/booking-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" ./mvnw test -Dtest=LegBookingServiceTest`
Expected: FAIL — compile error, `createLegBooking` doesn't accept 7 arguments yet.

- [ ] **Step 5: Update `LegBookingService.createLegBooking`**

Replace the method in `microservices/booking-service/src/main/java/com/travel2go/backend/service/LegBookingService.java`:

```java
    public Booking createLegBooking(String tripId, String legId, String quoteToken, Long amountPaise,
            String ownerUserId, String email, String phone) {
        Booking existing = findRelevantBooking(legId);
        if (existing != null && isActive(existing)) {
            if (ownerUserId.equals(existing.getOwnerUserId())) {
                return existing;
            }
            throw new LegBookingConflictException("Booking for legId " + legId + " is already claimed by another user");
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
                    .email(email)
                    .phone(phone)
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
                .email(email)
                .phone(phone)
                .status("PENDING")
                .bookingDate(new Date())
                .build();

        return bookingRepository.save(booking).block();
    }
```

- [ ] **Step 6: Update `LegBookingController` to pass the new fields through**

In `microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java`, replace the `createLegBooking` handler body's call:

```java
    @PostMapping
    public ResponseEntity<LegBookingResponse> createLegBooking(@RequestBody LegBookingRequest request) {
        try {
            Booking booking = legBookingService.createLegBooking(
                    request.getTripId(), request.getLegId(), request.getQuoteToken(),
                    request.getAmountPaise(), currentUserId(), request.getEmail(), request.getPhone());
            return ResponseEntity.ok(new LegBookingResponse(booking.getLegId(), booking.getStatus()));
        } catch (LegBookingRejectedException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
        } catch (LegBookingConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd microservices/booking-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" ./mvnw test -Dtest=LegBookingServiceTest`
Expected: PASS, all tests including the two new ones.

- [ ] **Step 8: Rebuild `common-models` and re-verify Task 3's trip-service tests now compile and pass**

Run: `cd microservices/common-models && ./mvnw -q clean install && cd ../trip-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" BOOKING_SERVICE_URL="http://localhost:8080" ./mvnw test -Dtest=TripServiceTest`
Expected: PASS, all tests including `bookLeg_threadsEmailAndPhoneIntoLegBookingRequest`.

- [ ] **Step 9: Run the full booking-service suite**

Run: `cd microservices/booking-service && DOCKER_HOST=tcp://localhost:2375 JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" NOTIFICATION_SERVICE_URL="http://localhost:8081" IDENTITY_SERVICE_URL="http://localhost:8082" PACKAGE_SERVICE_URL="http://localhost:8083" ./mvnw clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingRequest.java microservices/booking-service/src/main/java/com/travel2go/backend/service/LegBookingService.java microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java microservices/booking-service/src/test/java/com/travel2go/backend/service/LegBookingServiceTest.java
git commit -m "MVP: LegBookingRequest carries email/phone, persisted onto Booking"
```

---

### Task 5: booking-service sends a real leg-booking confirmation

**Files:**
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/client/NotificationClient.java`
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/client/NotificationClientFallbackFactory.java`
- Modify: `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/LegConfirmedConsumer.java`
- Modify: `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/LegConfirmedConsumerTest.java`
- Modify: `microservices/notification-service/src/main/java/com/travel2go/backend/controller/NotificationController.java`
- Modify: `microservices/notification-service/src/main/java/com/travel2go/backend/service/EmailService.java`
- Create: `microservices/notification-service/src/test/java/com/travel2go/backend/service/EmailServiceTest.java`

**Interfaces:**
- Produces: `NotificationClient.sendLegBookingConfirmation(LegBookingConfirmationRequest request)` — new Feign method, `POST /api/notifications/send-leg-booking-confirmation`.
- Produces: `NotificationClient.LegBookingConfirmationRequest(String email, String legType, long amountPaise, String bookingReference)` — static inner class, mirroring `NotificationRequest`'s existing pattern in the same interface.
- Produces: `EmailService.sendLegBookingConfirmation(String email, String legType, long amountPaise, String bookingReference)` — plain-text send via `SimpleMailMessage`, no PDF, no GCS archival (spec Decision 5).

- [ ] **Step 1: Write the failing test for the consumer**

Add to `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/LegConfirmedConsumerTest.java` (add the new imports and `@Mock private NotificationClient notificationClient;` field, pass it into the constructor in `setUp()` — see Step 4 for the full updated constructor call):

```java
    @Test
    void onLegConfirmed_confirmingBookingSendsNotification() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L)
                .email("traveller@example.com").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(notificationClient).sendLegBookingConfirmation(org.mockito.ArgumentMatchers.argThat(req ->
                "traveller@example.com".equals(req.email) && req.amountPaise == 150000L));
    }

    @Test
    void onLegConfirmed_duplicateDeliverySendsNotificationOnlyOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L)
                .email("traveller@example.com").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));
        pending.setStatus("CONFIRMED");
        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(notificationClient, times(1)).sendLegBookingConfirmation(any());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd microservices/booking-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" ./mvnw test -Dtest=LegConfirmedConsumerTest`
Expected: FAIL — compile error, `NotificationClient` doesn't exist as a constructor argument on `LegConfirmedConsumer` yet, and `sendLegBookingConfirmation` doesn't exist.

- [ ] **Step 3: Add the new method to `NotificationClient`**

Replace the full content of `microservices/booking-service/src/main/java/com/travel2go/backend/client/NotificationClient.java`:

```java
package com.travel2go.backend.client;

import com.travel2go.backend.dto.BookingRequest;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.model.HolidayPackage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", url = "${NOTIFICATION_SERVICE_URL}",
        fallbackFactory = NotificationClientFallbackFactory.class)
public interface NotificationClient {

    @PostMapping("/api/notifications/send-confirmation")
    void sendBookingConfirmation(@RequestBody NotificationRequest request);

    @PostMapping("/api/notifications/send-leg-booking-confirmation")
    void sendLegBookingConfirmation(@RequestBody LegBookingConfirmationRequest request);

    // We create a local DTO to hold all data
    public static class NotificationRequest {
        public BookingRequest bookingRequest;
        public HolidayPackage holidayPackage;
        public GlobalSettings globalSettings;
        public Booking booking;
        
        public NotificationRequest(BookingRequest req, HolidayPackage pkg, GlobalSettings settings, Booking bk) {
            this.bookingRequest = req;
            this.holidayPackage = pkg;
            this.globalSettings = settings;
            this.booking = bk;
        }
        
        public NotificationRequest() {}
    }

    public static class LegBookingConfirmationRequest {
        public String email;
        public String legType;
        public long amountPaise;
        public String bookingReference;

        public LegBookingConfirmationRequest(String email, String legType, long amountPaise, String bookingReference) {
            this.email = email;
            this.legType = legType;
            this.amountPaise = amountPaise;
            this.bookingReference = bookingReference;
        }

        public LegBookingConfirmationRequest() {}
    }
}
```

- [ ] **Step 4: Restructure `NotificationClientFallbackFactory`**

Replace the full content of `microservices/booking-service/src/main/java/com/travel2go/backend/client/NotificationClientFallbackFactory.java`:

```java
package com.travel2go.backend.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * B5: booking -> notification is fire-and-forget. The booking is already persisted
 * before the confirmation is sent, so a notification-service outage must NOT fail
 * the booking. This fallback logs and swallows, and combined with the circuit
 * breaker it fast-fails instead of hanging the booking thread while notification
 * is down.
 *
 * Only these notification calls get a swallowing fallback. The money path
 * (trip -> booking) and the reads (package/settings) intentionally have none, so
 * they fail loudly and leave their callers to recover.
 */
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationClientFallbackFactory.class);

    @Override
    public NotificationClient create(Throwable cause) {
        return new NotificationClient() {
            @Override
            public void sendBookingConfirmation(NotificationRequest request) {
                log.warn("notification-service unavailable; booking confirmation not sent (booking is unaffected). Reason: {}",
                        cause.toString());
            }

            @Override
            public void sendLegBookingConfirmation(LegBookingConfirmationRequest request) {
                log.warn("notification-service unavailable; leg-booking confirmation not sent (booking is unaffected). Reason: {}",
                        cause.toString());
            }
        };
    }
}
```

- [ ] **Step 5: Update `LegConfirmedConsumer`**

Replace the full content of `microservices/booking-service/src/main/java/com/travel2go/backend/consumer/LegConfirmedConsumer.java`:

```java
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
 * Consumes trip-service's leg.confirmed event (P1.4) and confirms the
 * matching Booking, then sends a real confirmation notification (MVP 1C -
 * replaces the earlier "not yet implemented" log line). Idempotent via the
 * same status-guard as before: a redelivered leg.confirmed finds the
 * Booking already CONFIRMED and returns before ever reaching either the
 * save or the notification call, so no new dedupe state is needed.
 *
 * A notification-service outage does not affect the Booking - B5's circuit
 * breaker + fallback factory already absorbs that failure by logging and
 * swallowing, matching the money-path guarantee that a notification never
 * rolls back a confirmation.
 *
 * An event for a legId with no matching Booking, or any other unexpected
 * exception, propagates (P1.4) - this queue's RabbitMQConfig gives it a
 * dead-letter exchange + bounded retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegConfirmedConsumer {

    private final BookingRepository bookingRepository;
    private final NotificationClient notificationClient;

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

        notificationClient.sendLegBookingConfirmation(new NotificationClient.LegBookingConfirmationRequest(
                booking.getEmail(), booking.getLegId(), booking.getAmountPaise(), booking.getId()));

        log.info("Booking {} confirmed (legId {}) and confirmation notification sent",
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

Note `booking.getLegId()` is passed as the "leg type" positional argument above for simplicity (the `Booking` model has no separate `legType` field — `legId` is the closest identifying string available without a new field). If you find this confusing, it's fine as-is: the email body only needs *some* human-readable reference, not a strict type enum.

- [ ] **Step 6: Update `LegConfirmedConsumerTest`'s `setUp()`**

In `microservices/booking-service/src/test/java/com/travel2go/backend/consumer/LegConfirmedConsumerTest.java`, add the import `import com.travel2go.backend.client.NotificationClient;`, add `@Mock private NotificationClient notificationClient;` alongside the existing `@Mock private BookingRepository bookingRepository;`, and update `setUp()`:

```java
    @BeforeEach
    void setUp() {
        consumer = new LegConfirmedConsumer(bookingRepository, notificationClient);
        lenient().when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd microservices/booking-service && JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" ./mvnw test -Dtest=LegConfirmedConsumerTest`
Expected: PASS, all tests including the two new ones.

- [ ] **Step 8: Add the notification-service endpoint and email method**

In `microservices/notification-service/src/main/java/com/travel2go/backend/service/EmailService.java`, add this method (keep everything else in the file unchanged):

```java
    public void sendLegBookingConfirmation(String email, String legType, long amountPaise, String bookingReference) {
        if (email == null || email.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your Travel2Go booking is confirmed");
        message.setText(String.format(
                "Great news - your booking is confirmed!\n\n" +
                "Reference: %s\n" +
                "Type: %s\n" +
                "Amount paid: Rs. %.2f\n\n" +
                "Thank you for booking with Travel2Go.",
                bookingReference, legType, amountPaise / 100.0));
        mailSender.send(message);
    }
```

In `microservices/notification-service/src/main/java/com/travel2go/backend/controller/NotificationController.java`, add this endpoint and DTO (keep everything else in the file unchanged):

```java
    @PostMapping("/send-leg-booking-confirmation")
    public ResponseEntity<Void> sendLegBookingConfirmation(@RequestBody LegBookingConfirmationRequest request) {
        try {
            emailService.sendLegBookingConfirmation(
                    request.email, request.legType, request.amountPaise, request.bookingReference);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class LegBookingConfirmationRequest {
        public String email;
        public String legType;
        public long amountPaise;
        public String bookingReference;
    }
```

- [ ] **Step 9: Write the failing test for `EmailService.sendLegBookingConfirmation`**

Create `microservices/notification-service/src/test/java/com/travel2go/backend/service/EmailServiceTest.java`:

```java
package com.travel2go.backend.service;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private Storage storage;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, storage);
    }

    @Test
    void sendLegBookingConfirmation_sendsPlainTextEmailToRecipient() {
        emailService.sendLegBookingConfirmation("traveller@example.com", "leg-1", 150000L, "booking-1");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("traveller@example.com");
        assertThat(captor.getValue().getSubject()).contains("confirmed");
        assertThat(captor.getValue().getText()).contains("booking-1");
    }

    @Test
    void sendLegBookingConfirmation_skipsSendWhenEmailIsBlank() {
        emailService.sendLegBookingConfirmation(null, "leg-1", 150000L, "booking-1");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd microservices/notification-service && ./mvnw test -Dtest=EmailServiceTest` (check `application.properties` for any required env vars first — if none are required for a pure unit test, this should run with no extra setup)
Expected: PASS.

- [ ] **Step 11: Run the full notification-service and booking-service suites**

Run: `cd microservices/notification-service && ./mvnw clean verify` (with whatever env vars this service's `application.properties` requires — check for bare `${VAR}` placeholders with no default first, same as every other service in this repo)
Run: `cd ../booking-service && DOCKER_HOST=tcp://localhost:2375 JWT_SECRET="test-jwt-secret-0123456789abcdef0123456789abcdef" QUOTE_TOKEN_SECRET="test-quote-token-secret-0123456789abcdef0123456789abcdef" NOTIFICATION_SERVICE_URL="http://localhost:8081" IDENTITY_SERVICE_URL="http://localhost:8082" PACKAGE_SERVICE_URL="http://localhost:8083" ./mvnw clean verify`
Expected: BUILD SUCCESS on both.

- [ ] **Step 12: Commit**

```bash
git add microservices/booking-service/src/main/java/com/travel2go/backend/client/NotificationClient.java microservices/booking-service/src/main/java/com/travel2go/backend/client/NotificationClientFallbackFactory.java microservices/booking-service/src/main/java/com/travel2go/backend/consumer/LegConfirmedConsumer.java microservices/booking-service/src/test/java/com/travel2go/backend/consumer/LegConfirmedConsumerTest.java microservices/notification-service/src/main/java/com/travel2go/backend/controller/NotificationController.java microservices/notification-service/src/main/java/com/travel2go/backend/service/EmailService.java microservices/notification-service/src/test/java/com/travel2go/backend/service/EmailServiceTest.java
git commit -m "MVP 1C: send a real leg-booking confirmation email instead of logging a TODO"
```

---

### Task 6: frontend API layer

**Files:**
- Modify: `frontend/src/services/api.js`

**Interfaces:**
- Produces: `createTrip(payload)`, `addLeg(tripId, payload)`, `bookLeg(tripId, legId, payload)`, `createPaymentOrder(payload)`, `getPaymentStatus(legId)` — all named exports alongside the existing default `api` export. Consumed by Task 7's `Checkout.jsx`.

- [ ] **Step 1: Add the domain functions**

Append to the end of `frontend/src/services/api.js` (after the existing `export default api;` line — add these as additional named exports in the same file):

```javascript
export const createTrip = (payload) => api.post('/trips', payload).then((res) => res.data);

export const addLeg = (tripId, payload) =>
  api.post(`/trips/${tripId}/legs`, payload).then((res) => res.data);

export const bookLeg = (tripId, legId, payload) =>
  api.post(`/trips/${tripId}/legs/${legId}/book`, payload).then((res) => res.data);

export const createPaymentOrder = (payload) =>
  api.post('/payments/order', payload).then((res) => res.data);

export const getPaymentStatus = (legId) =>
  api.get(`/payments/${legId}`).then((res) => res.data);
```

Note: `api`'s `baseURL` already ends in `/api` (see `getBaseUrl()` at the top of this file), so these paths correctly omit the leading `/api` segment — matches how every other page in this codebase calls `api.post('/auth/login', ...)` etc. (no `/api` prefix in the call site).

- [ ] **Step 2: Manual verification**

There is no test framework in this repo's frontend (per the design spec's Decision 9) — verify by starting the dev server (`cd frontend && npm run dev`) and confirming the app still boots with no console errors from this file (a syntax error in a new export would break the whole bundle). This is a pure addition with no behavior change to existing exports, so a clean dev-server boot is sufficient for this task; the real functional exercise happens in Task 7.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/services/api.js
git commit -m "MVP: add trip/leg/booking/payment API functions to the frontend service layer"
```

---

### Task 7: frontend checkout flow

**Files:**
- Create: `frontend/src/pages/Checkout.jsx`
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/.env` (create if it doesn't exist — check first with `ls frontend/.env*`)

**Interfaces:**
- Consumes: `createTrip`, `addLeg`, `bookLeg`, `createPaymentOrder`, `getPaymentStatus` from Task 6's `frontend/src/services/api.js`.
- Produces: a new route `/checkout` in `App.jsx`, protected by the existing `ProtectedRoute` wrapper.

- [ ] **Step 1: Add the Razorpay public key to the frontend env**

Check whether `frontend/.env` exists: `ls frontend/.env* 2>/dev/null || echo "none found"`. If none exists, create `frontend/.env` with:

```
VITE_RAZORPAY_KEY_ID=rzp_test_replace_with_real_key_id
```

If `frontend/.env` already exists, add this line to it instead of overwriting the file. This is a placeholder value for local development — the real `RAZORPAY_KEY_ID` (public, safe for frontend) must be set in the actual build environment before a real deploy; note this explicitly in your task report as a hand-back item (matches this repo's established pattern for env-based secrets/config that can't be committed with real values).

- [ ] **Step 2: Create `Checkout.jsx`**

```jsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import MainLayout from '../components/MainLayout';
import { createTrip, addLeg, bookLeg, createPaymentOrder, getPaymentStatus } from '../services/api';

const LEG_TYPES = ['RAIL', 'FLIGHT', 'HOTEL', 'CAB'];

const decodeJwtPayload = (token) => {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
};

const loadRazorpayScript = () =>
  new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });

const STEP = {
  BUILD: 'BUILD',
  CONTACT: 'CONTACT',
  PROCESSING: 'PROCESSING',
  CONFIRMED: 'CONFIRMED',
  FAILED: 'FAILED',
};

const Checkout = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState(STEP.BUILD);
  const [legType, setLegType] = useState(LEG_TYPES[0]);
  const [pricePaise, setPricePaise] = useState(150000);
  const [email, setEmail] = useState(() => {
    const token = localStorage.getItem('token');
    const payload = token ? decodeJwtPayload(token) : null;
    return payload && payload.sub && payload.sub.includes('@') ? payload.sub : '';
  });
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [tripId, setTripId] = useState(null);
  const [legId, setLegId] = useState(null);

  const handleBuildLeg = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const trip = await createTrip({
        title: `${legType} booking`,
        travellerIds: [],
        origin: 'N/A',
        destination: 'N/A',
      });
      const leg = await addLeg(trip.id, {
        type: legType,
        pricePaise: Number(pricePaise),
        startAt: new Date().toISOString(),
        endAt: new Date().toISOString(),
        metadata: {},
      });
      setTripId(trip.id);
      setLegId(leg.id);
      setStep(STEP.CONTACT);
    } catch (err) {
      setError('Could not create your trip/leg. Please try again.');
    }
  };

  const pollPaymentStatus = (bookingRef, attemptsLeft) => {
    if (attemptsLeft <= 0) {
      setStep(STEP.FAILED);
      setError('Still processing - please check back shortly.');
      return;
    }
    getPaymentStatus(bookingRef)
      .then((payment) => {
        if (payment.status === 'CAPTURED') {
          setStep(STEP.CONFIRMED);
        } else if (payment.status === 'FAILED') {
          setStep(STEP.FAILED);
          setError('Payment failed. Please try again.');
        } else {
          setTimeout(() => pollPaymentStatus(bookingRef, attemptsLeft - 1), 3000);
        }
      })
      .catch(() => {
        setTimeout(() => pollPaymentStatus(bookingRef, attemptsLeft - 1), 3000);
      });
  };

  const handleContactSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setStep(STEP.PROCESSING);
    try {
      const booked = await bookLeg(tripId, legId, { email, phone });
      const payment = await createPaymentOrder({
        bookingRef: booked.id,
        amountPaise: booked.pricePaise,
        method: 'razorpay',
        quoteToken: booked.quoteToken,
      });

      if (payment.status === 'REJECTED') {
        setStep(STEP.FAILED);
        setError('Your quote expired. Please start again.');
        return;
      }

      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        setStep(STEP.FAILED);
        setError('Could not load the payment provider. Please try again.');
        return;
      }

      const razorpay = new window.Razorpay({
        key: import.meta.env.VITE_RAZORPAY_KEY_ID,
        order_id: payment.providerRef,
        amount: payment.amountPaise,
        currency: 'INR',
        name: 'Travel2Go',
        handler: () => {
          // Do NOT trust this callback - it only means the modal closed
          // successfully client-side. The real answer is the webhook,
          // surfaced via the status poll below.
        },
        modal: {
          ondismiss: () => {
            pollPaymentStatus(booked.id, 30);
          },
        },
      });
      razorpay.on('payment.failed', () => {
        pollPaymentStatus(booked.id, 30);
      });
      razorpay.open();
      pollPaymentStatus(booked.id, 30);
    } catch (err) {
      setStep(STEP.FAILED);
      if (err.response && err.response.status === 409) {
        setError('This leg is already claimed by another user.');
      } else {
        setError('Something went wrong while booking. Please try again.');
      }
    }
  };

  return (
    <MainLayout>
      <div className="max-w-lg mx-auto py-12 px-4">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Book a leg</h1>

        {error && (
          <div className="mb-4 bg-red-50 border-l-4 border-red-400 p-4">
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {step === STEP.BUILD && (
          <form className="space-y-4" onSubmit={handleBuildLeg}>
            <div>
              <label className="block text-sm font-medium text-gray-700">Type</label>
              <select
                value={legType}
                onChange={(e) => setLegType(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              >
                {LEG_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Price (paise)</label>
              <input
                type="number"
                required
                min="1"
                value={pricePaise}
                onChange={(e) => setPricePaise(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              />
            </div>
            <button
              type="submit"
              className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              Continue
            </button>
          </form>
        )}

        {step === STEP.CONTACT && (
          <form className="space-y-4" onSubmit={handleContactSubmit}>
            <div>
              <label className="block text-sm font-medium text-gray-700">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Phone</label>
              <input
                type="tel"
                required
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              />
            </div>
            <button
              type="submit"
              className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              Pay now
            </button>
          </form>
        )}

        {step === STEP.PROCESSING && (
          <div className="text-center py-12">
            <p className="text-gray-600">Confirming your payment...</p>
          </div>
        )}

        {step === STEP.CONFIRMED && (
          <div className="text-center py-12">
            <p className="text-xl font-semibold text-green-700">Booking confirmed!</p>
            <button onClick={() => navigate('/')} className="mt-4 text-blue-600 underline">
              Back home
            </button>
          </div>
        )}

        {step === STEP.FAILED && (
          <div className="text-center py-12">
            <button
              onClick={() => setStep(STEP.CONTACT)}
              className="py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              Try again
            </button>
          </div>
        )}
      </div>
    </MainLayout>
  );
};

export default Checkout;
```

Note on `booked.pricePaise`/`booked.quoteToken` used above in `handleContactSubmit`: `bookLeg`'s response is the *booked* `Leg` returned by trip-service's `POST /.../book` endpoint (Tasks 1 and 3), which has both `pricePaise` and `quoteToken` fields — these names are correct as written.

- [ ] **Step 3: Add the route to `App.jsx`**

In `frontend/src/App.jsx`, add the import `import Checkout from './pages/Checkout';` alongside the other page imports, and add this route inside `<Routes>`, next to the other `ProtectedRoute`-wrapped routes:

```jsx
        <Route
          path="/checkout"
          element={
            <ProtectedRoute>
              <Checkout />
            </ProtectedRoute>
          }
        />
```

- [ ] **Step 4: Manual verification (per Decision 9 — no automated frontend tests in this repo)**

This is the real acceptance test for the whole plan. With all backend services running locally (payment-service needs `DOCKER_HOST`/Postgres per its own setup; trip-service, booking-service, notification-service, api-gateway all need their usual env vars; RabbitMQ running) and `frontend`'s dev server up (`npm run dev`):

1. Log in, navigate to `/checkout`.
2. Fill in the leg type/price, continue.
3. Fill in email/phone, click "Pay now."
4. Confirm the Razorpay modal opens with the correct amount.
5. Complete a test payment (Razorpay test mode card, if `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET` are sandbox test keys) or close the modal to trigger the poll path.
6. Confirm the UI shows "Confirming your payment..." and only flips to "Booking confirmed!" once payment-service's webhook has actually processed a capture (check payment-service logs for the webhook hit, and `GET /api/payments/{legId}` returning `CAPTURED`) — never immediately on the Razorpay modal closing.
7. Check the configured mailbox/notification-service logs for the leg-booking confirmation email.

Record the outcome of this manual walkthrough in your task report — screenshots are not required, but a clear pass/fail per step is.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/Checkout.jsx frontend/src/App.jsx frontend/.env
git commit -m "MVP: add the checkout flow (contact form -> book -> Razorpay -> status-poll confirmation)"
```

---

## Final acceptance check (maps to the design spec's criteria)

1. `grep -rn '\.issue(' microservices --include='*.java' | grep -v /target/ | grep -v 'public String issue'` → shows the new call in `TripService.addLeg` (Task 1).
2. `grep -n 'Path=/api/payments' microservices/api-gateway/src/main/resources/application.properties` → both `routes[9]` (webhook) and `routes[10]` (broad) present (Task 2).
3. `grep -rn 'not yet implemented' microservices/booking-service/src/main --include='*.java'` → no matches (Task 5).
4. `grep -rn 'payments/order\|leg-bookings\|/api/trips\|razorpay\|providerRef' frontend/src --include='*.js*'` → hits in `api.js` and `Checkout.jsx` (Tasks 6-7).
5. `cd microservices/trip-service && ./mvnw -q clean verify` → BUILD SUCCESS (Tasks 1, 3).
6. `cd microservices/payment-service && ./mvnw -q clean verify` → BUILD SUCCESS (unaffected by this plan, confirms nothing broke).
7. `cd microservices/booking-service && ./mvnw -q clean verify` → BUILD SUCCESS (Tasks 4, 5).
8. `cd microservices/notification-service && ./mvnw -q clean verify` → BUILD SUCCESS (Task 5).
9. `cd microservices/api-gateway && ./mvnw -q clean verify` → BUILD SUCCESS (Task 2).
10. Manual browser walkthrough (Task 7, Step 5) passes end to end.
