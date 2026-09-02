# P1.2 — booking-service independent validation + payment-gated confirmation (design)

Status: approved for planning
Scope: `microservices/booking-service` (leg-booking write path + a new `payment.captured` consumer), `microservices/trip-service` (Leg confirmation — scope expansion, see below), `common-models` (`Booking`, `Payment`), a small closing fix in `microservices/payment-service` (ownership on `getStatus`). Does not touch P1.3 (Postgres ledger) or P1.4 (saga/outbox).

## Source brief

`P1.2-booking-validation-gated-confirmation.md` — the detailed brief this design implements. Depends on P1.1 (`payment.captured` event, `CAPTURED` state — already on `main`).

## Current state (verified against the repo, 2026-09-02)

- `LegBookingController.createLegBooking`: builds a `Booking` directly from the request with **zero validation**, sets `status="CONFIRMED"` unconditionally, returns `LegBookingResponse{bookingId=saved.getId(), status}` — `bookingId` is the Firestore auto doc-id, **not** the `legId`.
- Identifier mismatch (real, pre-existing bug): `QuoteTokenService.issue(legId, pricePaise)` signs with subject=`legId`; `payment-service`'s `isValid(token, bookingRef, amountPaise)` only passes when `bookingRef == legId`. So the payment order's `bookingRef` must be the `legId`, but `LegBookingController` currently hands back the doc-id instead.
- **Scope-expansion finding (approved by user):** `TripService.bookLeg()` — the *only* caller of `booking-service`'s leg-booking endpoint — sets `Leg.status = "CONFIRMED"` synchronously, immediately after the Feign call succeeds, with zero payment involvement. This is the same premature-confirmation bug the brief fixes in booking-service, one layer up, in a file the brief doesn't mention. Left unfixed, `booking-service`'s `Booking.status` would correctly stay `PENDING` while `trip-service`'s `Leg.status` (what the frontend actually reads) would still say `CONFIRMED` — a real, visible inconsistency. **In scope for this design.**
- `TripService.bookLeg()` is also the direct consumer of `LegBookingResponse.bookingId` (`leg.setSupplierRef(response.getBookingId())`) — the brief frames the reference-consistency fix as a "confirm with the frontend" concern, but the *immediate* consumer is trip-service's own code, which must be updated in this same change regardless of frontend timing.
- `Booking` (`common-models`): has `tripId`, `legId`, `quoteToken`, `amountPaise`, `feePaise`, `status`, `bookingDate` for the leg-booking fields (nullable, shared with legacy package-booking fields). No `ownerUserId`/`providerPaymentId`/`confirmedAt`.
- `Leg` (`common-models`): `status` is a free-form `String` (`SEARCHING | SELECTED | WAITLISTED | CONFIRMED | CANCELLED | COMPLETED | DISRUPTED` per comment) — adding a new value is additive, no enum to change.
- `booking-service`'s `RabbitMQConfig` declares only its own pre-existing `booking.exchange` (`DirectExchange`, outbound `booking.initiated`) — it does **not** declare `trip.exchange` at all. Needs a fresh declaration, mirroring `payment-service`'s Task-5 pattern (idempotent re-declare of the same exchange trip-service/payment-service already own).
- `trip-service`'s `RabbitMQConfig` already declares `trip.exchange` (publisher role only — no queue/listener yet).
- `booking-service` already depends on `platform-security` (A1, for `JwtUtil`) and already has `SPRING_RABBITMQ_*` wired (A3) — but does **not** have `quote.token.secret` set, so `QuoteTokenService`'s `@ConditionalOnProperty(name = "quote.token.secret")` bean is currently absent there.
- `TripController`'s `currentUserId()` pattern (`SecurityContextHolder.getContext().getAuthentication().getName()`) and `TripService.assertOwner` are the existing precedent for ownership capture/enforcement — reused here. `assertOwner` has **no** `ROLE_ADMIN` bypass today; P1.2 introduces the first admin-bypass ownership check in the codebase.
- `trip-service`'s `FeignConfig` (a `RequestInterceptor`) already propagates the `Authorization` header on every Feign call, including `BookingClient.createLegBooking` — confirmed the JWT reaches booking-service's `SecurityContext` unchanged.
- `.github/workflows/backend-deploy.yml`'s `QUOTE_TOKEN_SECRET` case currently scopes to `trip-service|payment-service` only.
- `LegBookingControllerTest` (existing) asserts `status=="CONFIRMED"` and the doc-id reference — both assertions must change as part of this work.

## Decisions

1. **Service split:** move `createLegBooking`'s logic into a new `LegBookingService` (mirrors `PaymentService` from P1.1) for testability; `LegBookingController` becomes thin.
2. **Rejection handling:** mirror `payment-service`'s exact pattern for consistency across the money path — invalid quote-token persists a `Booking{status=REJECTED}` audit record and returns `402`.
3. **Reference consistency:** `legId` becomes the canonical leg-booking reference. `LegBookingResponse.bookingId` is **renamed to `legId`** (its single consumer, `TripService.bookLeg()`, is updated in the same change — not deferred to "confirm with the frontend," since trip-service is the immediate, in-repo consumer). One booking per `legId`, enforced via create-idempotency (§4 below).
4. **Create-idempotency:** if a `Booking` already exists for a `legId`, `createLegBooking` returns it instead of creating a duplicate.
5. **Idempotency mechanism for both new consumers:** a status-guard (`if status != PENDING, no-op`) — no separate dedupe store. This mirrors the pattern P1.1's final review independently validated for `payment-service` itself (the status field *is* the idempotency mechanism); adding a second dedupe collection here would be inconsistent with that established precedent and unnecessary given P1.1's own dedupe already exists upstream.
6. **Trip-service scope expansion (user-approved):** `Leg` gets a new status value, **`PENDING`** (mirrors `Booking`'s own vocabulary rather than inventing a different word for the same wait-state — e.g. not `AWAITING_PAYMENT`). `TripService.bookLeg()` sets `Leg.status = "PENDING"` instead of `"CONFIRMED"`. A **new, independent** RabbitMQ consumer in trip-service (own queue, same `trip.exchange` / `payment.captured` routing key — fan-out from payment-service's single publish, not a synchronous booking→trip callback) flips `Leg.status` `PENDING → CONFIRMED` idempotently. No notification from this consumer — booking-service's consumer already sends it; a second send would duplicate.
7. **New read endpoint:** `GET /api/leg-bookings/{legId}` (booking-service) — did not exist before; added because acceptance criterion #5 ("ownerUserId ... enforced on booking reads") has nothing to enforce against without one. Mirrors `payment-service`'s `GET /{bookingRef}` shape and gives the frontend a way to poll leg-booking status, matching P1.1's polling design.
8. **Ownership enforcement:** `currentUserId()` helper (`SecurityContextHolder`, same as `TripController`) captures `ownerUserId` at write time in both `booking-service` (`createLegBooking`) and `payment-service` (`createOrder`). Reads (`GET /api/leg-bookings/{legId}`, `payment-service`'s `getStatus`) enforce `ownerUserId == JWT subject`, with a `ROLE_ADMIN` bypass (checked via the authenticated principal's granted authorities) — the first such bypass in the codebase, since `TripService.assertOwner` has none today. Non-owner, non-admin → `403`.
9. **Consumer robustness:** an unknown `legId`/`bookingRef` (event arrives before the record exists — an ordering race that shouldn't happen but must not crash) is nacked with a bounded requeue (or dead-lettered), never silently dropped, in both new consumers.

## Architecture

```
LegBookingController
  POST /api/leg-bookings           -> LegBookingService.createLegBooking(...)
  GET  /api/leg-bookings/{legId}   -> LegBookingService.getBooking(legId, ownerUserId)  [NEW]

LegBookingService (new, mirrors PaymentService)
  createLegBooking(tripId, legId, quoteToken, amountPaise, ownerUserId) -> Booking
  getBooking(legId, requestingUserId) -> Booking   [ownership-enforced]

PaymentCapturedConsumer (booking-service, new)
  @RabbitListener(queues = "booking.payment-captured")
  onPaymentCaptured(PaymentCapturedEvent) -> confirms Booking + sends notification, idempotent

PaymentCapturedConsumer (trip-service, new — separate class, separate queue, same event)
  @RabbitListener(queues = "trip.payment-captured")
  onPaymentCaptured(PaymentCapturedEvent) -> flips matching Leg to CONFIRMED, idempotent
```

`PaymentCapturedEvent(String bookingRef, String providerPaymentId, long amountPaise)` — the exact record `payment-service` already publishes (P1.1); both new consumers deserialize the same shape independently (no shared library needed, small enough to duplicate the record definition per-service the way `payment-service`/`trip-service` already do for their own local event types).

## Data flow

1. `trip-service.bookLeg()` → `booking-service POST /api/leg-bookings { tripId, legId, quoteToken, amountPaise }`:
   - `quoteTokenService.isValid(quoteToken, legId, amountPaise)` (independent re-check). Invalid → `Booking{REJECTED}` persisted, `402`.
   - `feePaise = 0L` (G1).
   - `ownerUserId` captured from `SecurityContextHolder` (JWT propagated by trip-service's `FeignConfig`).
   - Existing `Booking` for this `legId`? Return it (idempotent create), don't touch the provider/repo further.
   - Persist `Booking{status=PENDING, legId, tripId, quoteToken, amountPaise, feePaise=0, ownerUserId, bookingDate}`.
   - Return `LegBookingResponse{legId, status=PENDING}`.
2. `trip-service.bookLeg()` sets `Leg.status = "PENDING"`, `Leg.supplierRef = response.getLegId()`.
3. Traveller pays via P1.1's flow (`bookingRef == legId`, per the identifier-consistency fix).
4. On `payment.captured` (published once by payment-service, consumed independently by two listeners):
   - **booking-service**: find `Booking` by `legId == event.bookingRef()`. Unknown → nack/dead-letter, never drop. Already `CONFIRMED` → no-op, ack. Else: verify `booking.amountPaise == event.amountPaise()` (independent second check, null-guarded for legacy non-leg bookings); mismatch → log as alert, ack, don't confirm. Match → `status=CONFIRMED`, `providerPaymentId`, `confirmedAt=now`, save; then `notificationClient.sendBookingConfirmation(...)` (existing B5-resilient Feign client — a notification outage must not fail confirmation, matching the existing fallback-factory pattern).
   - **trip-service**: find `Leg` by matching `legId`. Unknown → nack/dead-letter. Already `CONFIRMED` → no-op. Else `status=CONFIRMED`, save. No notification.
5. `GET /api/leg-bookings/{legId}`: returns the `Booking` if `ownerUserId == JWT subject` or caller has `ROLE_ADMIN`; else `403`.
6. `payment-service`: `createOrder` captures `ownerUserId`; `getStatus` enforces it (`403` for a non-owner, `ROLE_ADMIN` bypass) — closes the P1.1-review IDOR.

## Config & secrets

- `booking-service/application.properties`: add `quote.token.secret=${QUOTE_TOKEN_SECRET}` (no in-source default — A2 pattern; `QuoteTokenService`'s existing `@PostConstruct` already fail-fasts at <32 bytes). Optionally `quote.token.ttl-ms` (shared class defaults to 15m if unset).
- `backend-deploy.yml`: add `booking-service` to the existing `QUOTE_TOKEN_SECRET` case (`trip-service|payment-service` → `trip-service|payment-service|booking-service`). No other service gets it.
- `booking-service`: new `RabbitMQConfig` addition declaring `trip.exchange` (`TopicExchange`, idempotent re-declare) + a durable queue `booking.payment-captured` bound with routing key `payment.captured`.
- `trip-service`: new queue `trip.payment-captured` bound to its already-declared `trip.exchange`, same routing key. No new secrets — trip-service already has `SPRING_RABBITMQ_*`.

## Model changes (`common-models`)

- `Booking`: add `ownerUserId` (String), `providerPaymentId` (String), `confirmedAt` (Date) — all nullable, legacy package-bookings unaffected.
- `Payment`: add `ownerUserId` (String), nullable.
- `Leg`: no field changes — `status` stays a free-form `String`; `"PENDING"` is a new accepted value, documented in the existing comment.

## Error handling

| Case | Result |
|---|---|
| Invalid/missing quote-token at create | `Booking{REJECTED}` persisted, `402` |
| Duplicate create for same `legId` | Returns existing booking, no new write |
| `payment.captured` for already-`CONFIRMED` booking/leg | No-op, ack (idempotent) |
| `payment.captured` amount mismatch | Not confirmed, logged as alert, ack |
| `payment.captured` for unknown `legId`/`bookingRef` | Nack-with-bounded-requeue or dead-letter — never silently dropped |
| Non-owner reads booking (`GET /api/leg-bookings/{legId}`) or payment status | `403` (`ROLE_ADMIN` bypasses) |
| Notification failure during booking confirmation | Booking still confirms (existing B5 Feign resilience) |

## Testing

- `LegBookingServiceTest` (new, Mockito — mirrors `PaymentServiceTest`'s style): valid request → `PENDING` + `ownerUserId` set, `feePaise=0`; invalid quote-token → `REJECTED`, `402`, nothing else touched; duplicate `legId` → idempotent, returns existing.
- `PaymentCapturedConsumerTest` (booking-service, new): capture confirms once + notifies once; duplicate delivery confirms/notifies exactly once; amount mismatch doesn't confirm; unknown booking doesn't crash (nack/dead-letter path exercised).
- `PaymentCapturedConsumerTest` (trip-service, new): capture flips `Leg` to `CONFIRMED` once; duplicate delivery is a no-op; unknown leg doesn't crash.
- `LegBookingControllerTest` (existing) updated: `PENDING` not `CONFIRMED`; `legId` reference not doc-id.
- `PaymentServiceTest`: new cases for `ownerUserId` capture on `createOrder` and enforcement on `getStatus` (owner succeeds, non-owner `403`, `ROLE_ADMIN` bypasses).
- `TripServiceTest`: `bookLeg` sets `Leg.status="PENDING"` (not `CONFIRMED`) and uses the renamed `legId` reference field.

## Acceptance criteria

Per brief §8, all 9 apply, plus the user-approved scope addition: `Leg.status` is no longer set to `CONFIRMED` synchronously in `bookLeg()`, and only transitions to `CONFIRMED` via trip-service's own `payment.captured` consumer.

## Out of scope (explicitly deferred)

- P1.3 (Postgres ledger) — the brief's own hand-back note applies: "the P1.1 idempotency race is now mitigated on the consumer side but is fully closed only when P1.3 adds a DB unique constraint."
- P1.4 (saga/outbox).
- Any change to the legacy `POST /api/bookings` (package-booking) path — untouched.
