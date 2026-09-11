# P1.4 — Saga + transactional outbox (design)

Status: approved for planning
Scope: payment-service gets a transactional outbox + relay, replacing the P1.3 `afterCommit()` direct-publish; booking-service and trip-service get a dead-letter exchange + bounded retry on their `payment.captured` consumers; confirmation ownership moves to single-owner choreography (trip confirms `Leg` and emits `leg.confirmed`, booking re-binds to consume that instead of `payment.captured`). Does not touch P1.5 (leg-booking notifications) or build a full saga orchestrator.

## Source

`P1.4-saga-outbox.md`, refreshed for merged P1.3 (`main` @ `849f28f`). Sequenced after P1.3 per `FIX-PLAN.md`'s own ordering; F1/F2 (CI gate) already merged, confirmed via `git log`.

## Current state (verified against the repo, 2026-09-11)

- `PaymentService.applyCaptured`/`refund` (both `@Transactional`) publish via `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()`, with a synchronous-immediate fallback when no transaction synchronization is active (added in P1.3's final-review fix pass, for bare-Mockito unit tests). This is better than a bare post-save publish but still not durable: a JVM death after commit and before `afterCommit()` runs loses the event permanently.
- Neither `booking-service`'s nor `trip-service`'s `PaymentCapturedConsumer` has a dead-letter exchange or bounded retry. Both wrap their entire listener body in a top-level `try/catch (Exception e)` that logs and returns — an unrecoverable message (including the "unknown legId" ordering race) is silently swallowed, never retried, never surfaced. Both classes' own doc comments already say so explicitly.
- Both consumers independently confirm their own aggregate (`Booking`, `Leg`) directly off `payment.captured` — no ordering guarantee or shared-fate between the two; a partial failure in one leaves them permanently diverged with no automated recovery path.
- Idempotency is already solid at the source: P1.3's `processed_webhook_events` PK + `INSERT ... ON CONFLICT DO NOTHING` closes the capture-side race; both consumers are already status-guarded (`PENDING` → confirm, anything else → no-op).
- `trip-service` remains Firestore-only — P1.3 only migrated `payment-service` to Postgres. No outbox infrastructure (Postgres, Flyway, JPA) exists in trip-service today.
- Neither `booking-service` nor `trip-service`'s `pom.xml` has any Testcontainers dependency yet — RabbitMQ-integration testing is new test infrastructure for both.
- `PaymentCapturedEvent` is duplicated locally in each consumer's own package (not shared via `common-models`) specifically so each service's Jackson `trustedPackages` can stay scoped to `com.travel2go.backend.consumer` — this pattern must be followed for the new `LegConfirmedEvent` too.
- Both existing `PaymentCapturedConsumerTest` classes have tests (`..._unknownLeg(Id)DoesNotThrow`, `..._unexpectedExceptionIsCaughtAndDoesNotPropagate`) that assert the *current* swallow-everything behavior — these assertions are the exact behavior this ticket removes, so both tests must be rewritten to assert the opposite (the listener now throws in both cases, so Spring's retry/DLQ machinery can act on it).

## Decisions

1. **Trip-service's `leg.confirmed` publish is best-effort, not outboxed** (user-approved). Standing up Postgres/Flyway/JPA in trip-service purely to give it an outbox would be a scope expansion on the order of a second P1.3, not in the original brief. After the Firestore `Leg` write succeeds, trip-service publishes `leg.confirmed` directly (the same shape of gap P1.3 just closed for payment-service, now knowingly and explicitly left open for trip-service, documented in hand-back as a candidate for a future ticket if trip-service ever gets its own relational store).
2. **Each service owns its own dead-letter exchange** (`booking.dlx`, `trip.dlx`), not one shared exchange — each service's `RabbitMQConfig` is already independently declared and evolves independently; sharing a DLX name across two unrelated services' configs would create an implicit coupling neither currently has.
3. **Outbox relay lives inside payment-service itself** as a `@Scheduled` component, not a separate deployable — matches the brief exactly and avoids introducing a new service for a single-table poller. `FOR UPDATE SKIP LOCKED` is required (not optional) because payment-service already runs up to 3 Cloud Run instances (`backend-deploy.yml`'s `--max-instances=3`), so more than one relay poller can be ticking concurrently against the same table.
4. **The `afterCommit()` block and its `isSynchronizationActive()` guard are deleted entirely**, not kept alongside the outbox. The guard existed solely to make the direct-publish safe to unit-test outside a real transaction; once the outbox row is written as a normal (non-publishing) transactional side effect, there is no more publish-time transaction-synchronization concern left to guard.
5. **New `LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise)`** is defined locally in trip-service (producer) and duplicated into booking-service's consumer package (consumer), mirroring the existing `PaymentCapturedEvent` local-copy pattern — keeps each service's `trustedPackages` scoped tight, no shared DTO module.
6. **Booking-service's queue is renamed**, not redeclared in place: `booking.payment-captured` → `booking.leg-confirmed`, bound to `trip.exchange` with routing key `leg.confirmed`. Trip-service's own consumer binding is unchanged (still listens on `payment.captured`) — trip is both a consumer of `payment.captured` and (new) a producer of `leg.confirmed`.
7. **Both consumers' top-level `try/catch` is removed.** The unknown-aggregate case changes from "log ERROR and return" to "log ERROR and throw" — Spring AMQP's retry interceptor (configured per Decision 8) catches it, retries with backoff, then routes it to the DLQ after retries are exhausted. Any other unexpected exception is no longer caught at all — it propagates the same way.
8. **Retry parameters exactly match the brief**: `max-attempts=4`, `initial-interval=1s`, `multiplier=2`, `max-interval=10s`, `default-requeue-rejected=false`. Applied identically to both services via `spring.rabbitmq.listener.simple.retry.*`.
9. **Compensation (refund) is human-triggered only** — no new code. The existing `POST /api/payments/{bookingRef}/refund` endpoint (P1.1, ROLE_ADMIN-gated) is the compensation path for a DLQ'd, unrecoverable post-capture failure; this is a runbook/hand-back note, not a build item.
10. **New RabbitMQ integration test infrastructure for booking-service and trip-service** (Testcontainers `rabbitmq` module) — required because `@RabbitListener` retry/DLQ behavior isn't exercisable through the existing pure-Mockito unit tests, which call the listener method directly and never go through Spring AMQP's container/retry interceptor at all.

## Architecture

```
payment-service
  applyCaptured/refund (@Transactional)
    -> payment/refund state update (unchanged)
    -> write OutboxEntry row (NEW - replaces afterCommit publish)
  OutboxRelay (@Scheduled, every 1-2s)
    -> SELECT ... FOR UPDATE SKIP LOCKED WHERE published_at IS NULL LIMIT n
    -> eventPublisher.publish(entry.type, deserialized payload)
    -> UPDATE outbox SET published_at = now() (success)
       / attempts = attempts + 1 (failure, left unpublished, retried next tick)

trip.exchange (topic, unchanged)
  routing key "payment.captured"  -> trip-service's PaymentCapturedConsumer (unchanged binding)
                                      confirms Leg, THEN publishes "leg.confirmed" (NEW, best-effort)
  routing key "leg.confirmed"     -> booking-service's consumer (NEW binding, was "payment.captured")
                                      confirms Booking

booking-service / trip-service RabbitMQConfig (each)
  main queue:  x-dead-letter-exchange -> <service>.dlx
  <service>.dlx -> <service>.payment-captured.dlq / <service>.leg-confirmed... (whichever queue)
  spring.rabbitmq.listener.simple.retry: max-attempts=4, 1s->10s backoff, no infinite requeue
```

## Data flow — outbox write + relay

1. `applyCaptured` (inside its existing `@Transactional`): after `markCaptured` succeeds, build the same `PaymentCapturedEvent` payload as today, serialize to JSON, insert an `outbox` row (`aggregate_id` = bookingRef, `type` = `"payment.captured"`, `payload`, `created_at = now()`, `published_at = NULL`). No publish call happens inline anymore.
2. Transaction commits. The outbox row is now durable regardless of what happens next.
3. `OutboxRelay.relay()` (separate `@Scheduled` thread, independent transaction per tick) selects a small batch of unpublished rows with `SKIP LOCKED`, deserializes each payload back to its event type by `type`, calls `PaymentEventPublisher.publish(type, event)`, and on success sets `published_at`. A publish failure (broker unreachable) leaves the row unpublished and increments `attempts` — picked up again next tick, no data loss, no manual intervention needed for a transient broker blip.
4. `refund` follows the identical shape for `payment.refunded`.

## Data flow — choreography

1. Payment captured → payment-service's outbox → relay publishes `payment.captured` to `trip.exchange`.
2. Trip-service's existing consumer (binding unchanged) confirms the `Leg` (status guard, amount check — all unchanged from today), then publishes `leg.confirmed` (bookingRef/legId, providerPaymentId, amountPaise) to `trip.exchange`, best-effort.
3. Booking-service's re-bound consumer (`booking.leg-confirmed` queue, routing key `leg.confirmed`) confirms the `Booking` — same status-guard/amount-check logic it has today, just triggered by the new event type.
4. A `Booking` can only reach CONFIRMED once its `Leg` already has — the two aggregates can no longer diverge into "leg confirmed but booking never heard about it" or vice versa from two independent listeners racing the same upstream event.

## Error handling

- **Outbox relay publish failure**: row stays unpublished, `attempts` increments, retried next tick indefinitely (a permanently-down broker is an ops incident, not something the relay itself needs to give up on — no dead-lettering for the relay's own publish step, since giving up would mean permanently losing a captured payment's confirmation).
- **Consumer unrecoverable failure** (unknown aggregate, unexpected exception): now throws. Spring AMQP retries per Decision 8's backoff; after 4 attempts, dead-lettered to the service's own DLQ. A DLQ entry is a paid-but-unconfirmed capture — alertable, human-triaged, compensated via refund if truly unrecoverable (Decision 9).
- **Trip's best-effort `leg.confirmed` publish failure**: logged loudly (ERROR), no retry, no DLQ (there's no consumer-side infrastructure for a publish-side failure) — this is the explicitly accepted gap from Decision 1. The `Leg` itself is still correctly CONFIRMED in Firestore; only the downstream `Booking` confirmation is at risk in this narrow window.
- **Amount mismatches, terminal-status guards**: all unchanged from today's behavior in every consumer.

## Testing

- **Payment-service**: extends the existing Testcontainers (real Postgres) suite. New tests prove: (a) after `applyCaptured` commits, an outbox row exists with `published_at IS NULL` and the correct payload — proving the event survives independently of any publish step; (b) invoking the relay's tick method directly publishes the row and sets `published_at`; (c) two relay ticks (simulating two concurrent instances) racing the same unpublished row only publish once — proving `SKIP LOCKED` does its job. The `afterCommit`-specific tests from P1.3 (which asserted deferred-until-commit publish timing) are removed since that mechanism no longer exists.
- **Booking-service, trip-service**: existing Mockito unit tests are rewritten so the two swallow-everything assertions become throw-assertions (the listener method now propagates on unknown-aggregate and on unexpected exceptions). New Testcontainers-backed (real RabbitMQ) integration tests prove the actual retry/DLQ wiring: a listener that always throws ends up, after 4 attempts with backoff, with the message in the service's DLQ, not requeued forever and not silently dropped. Booking-service's consumer tests are additionally updated to consume `LegConfirmedEvent` instead of `PaymentCapturedEvent`.
- **Redelivery/crash proof** (acceptance criterion 5): a test asserting that redelivering the same `payment.captured` event to a consumer that has already confirmed its aggregate is a true no-op (no second confirmation, no second downstream publish) — this already holds via the existing status-guard, verified explicitly under the new throw-based error handling to confirm the guard still short-circuits before any exception path is reached.

## Acceptance criteria

(Copied and confirmed applicable from the brief, all in scope for this design.)

1. `payment.captured`/`payment.refunded` are written to an outbox row in the same transaction as the capture/refund; the `afterCommit` direct-publish is removed; the relay is the sole publisher; a crash between commit and publish still results in delivery (next relay tick).
2. Both consumers have a DLQ + bounded retry; after exhausted retries a message is dead-lettered, never silently acked or infinitely requeued.
3. The ordering race (capture before booking/leg exists) retries then DLQs, not drops.
4. `Booking` cannot be CONFIRMED unless its `Leg` is (single-owner choreography via `leg.confirmed`).
5. At-least-once delivery + idempotent consumers ⇒ exactly-once effect, proven by a redelivery test.
6. Compensation (refund) path exists for post-capture unrecoverable failures, human-triggered (no new code — existing P1.1 refund endpoint).
7. G1 (`fee_paise=0`), G2 (quote-token gate), payment-gated confirmation, owner scoping, and RabbitMQ `trustedPackages` hardening all intact.

## Out of scope (explicitly deferred)

- A full saga orchestrator (payment → leg → booking → notification with compensations) — noted in the brief as a future escalation path if the flow grows, not built now.
- P1.5 (leg-booking notifications) — separate ticket, brief not yet in hand at time of writing.
- Giving trip-service its own transactional outbox (Decision 1) — would require adding Postgres/Flyway/JPA to trip-service, a scope expansion beyond this brief.
- Automatic compensation/refund — always human-triggered per Decision 9.
