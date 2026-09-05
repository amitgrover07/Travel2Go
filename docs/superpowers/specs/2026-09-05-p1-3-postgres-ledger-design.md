# P1.3 — Postgres money ledger (design)

Status: approved for planning
Scope: payment-service's `Payment` and `ProcessedWebhookEvent` move off Firestore onto a dedicated Cloud SQL Postgres instance, with Flyway-managed schema, JPA repositories, atomic webhook-capture idempotency (DB unique constraint, not check-then-act), plus `refunds`/`settlements` tables and a `ROLE_ADMIN` reconciliation endpoint. Does not touch any other service's Firestore usage, does not build the saga/outbox (P1.4), does not build leg-booking notifications (P1.5).

## Source

`P1.3-postgres-ledger.md`, sequenced after F1+F2 per `FIX-PLAN.md`.

## Current state (verified against the repo, 2026-09-05)

- `payment-service`'s only Firestore usage is `Payment` (`common-models`) and `ProcessedWebhookEvent` (payment-service's own `webhook` package) — confirmed via `grep -rln "com.travel2go.backend.model.Payment\b" microservices` returning only payment-service's own `PaymentController`/`PaymentRepository`/`PaymentService`/`PaymentServiceTest`. No cross-service dependency. Safe to delete `Payment` from `common-models` and recreate both entities inside payment-service.
- `PaymentService.applyWebhook` dedupes via `processedWebhookEventRepository.findById(dedupeKey).block() != null` — a real check-then-act race: two concurrent deliveries of the same webhook can both pass the check before either writes.
- `PaymentService.createOrder` already has P1.2's owner-aware idempotent-create (`findRelevantPayment` — prefers active/settled status over terminal, null-safe newest-tiebreak) and `PaymentConflictException` → 409. This logic is correct and carries forward unchanged, just re-backed by Postgres.
- `payment-service/pom.xml` has `spring-cloud-gcp-starter-data-firestore`, no JPA/Postgres/Flyway dependencies.
- `infra/terraform/service-accounts.tf` already creates `t2g-payment`; `infra/terraform/iam.tf` shows the per-SA role-grant pattern to follow for `roles/cloudsql.client`; `infra/terraform/variables.tf` sets `region = "asia-south2"` (data-localization requirement for the new Cloud SQL instance).
- Docker Desktop is installed locally but was not running (`docker ps` failed) — user confirmed they'll start it before implementation, needed for Testcontainers-based Postgres tests.

## Decisions

1. **Socket-factory Cloud SQL connectivity** (user-approved) — no VPC connector needed. Cloud Run gets `--add-cloudsql-instances=<connection-name>`, payment-service's datasource URL uses the Cloud SQL JDBC Socket Factory (`com.google.cloud.sql:postgres-socket-factory`), auth via `t2g-payment`'s `roles/cloudsql.client`.
2. **Entities move into payment-service** (user-approved) — delete `Payment` from `common-models` entirely; recreate `Payment` and `ProcessedWebhookEvent` as JPA `@Entity` classes in payment-service's own package (`com.travel2go.backend.model` within payment-service, no naming collision once the common-models copy is deleted). New `Refund` and `Settlement` entities join them.
3. **`spring-cloud-gcp-starter-data-firestore` is removed from payment-service's pom** — nothing else in the service uses Firestore once these two collections migrate.
4. **`PaymentService` becomes fully synchronous** — JPA is blocking; all `Flux`/`.block()` reactive wrapping is dropped in favor of plain `List`/`Optional` return types from Spring Data JPA repositories.
5. **Webhook-capture idempotency is a single `@Transactional` method**, exactly per the brief's §4:
   a. Insert into `processed_webhook_events(event_id)` (PK) — catch `DataIntegrityViolationException` → already processed, no-op, return.
   b. A conditional `@Modifying @Query` update: `UPDATE payments SET status='CAPTURED', provider_payment_id=:providerPaymentId, updated_at=now() WHERE id=:id AND status='CREATED'` — check the returned affected-row count; `0` means someone else already transitioned it (or it's not in `CREATED`), treat as no-op.
   c. Commit.
   d. Publish `payment.captured` **after** the transaction commits, never inside it (a broker hiccup must not roll back a real capture — full outbox durability is P1.4's job, not this ticket's).
6. **Order-create idempotency** keeps the existing `findRelevantPayment` application logic as primary mechanism (already correct), with `UNIQUE(provider_order_id)` as a DB-level safety net — the true concurrent-duplicate-order race is far rarer than duplicate webhook delivery, which is why the brief's emphasis (and this design's) is on the webhook path.
7. **Full brief scope** (user-approved) — `refunds` and `settlements` tables plus a `ROLE_ADMIN` reconciliation endpoint are built now, not deferred.
8. **Testcontainers** (user-approved, Docker Desktop to be started before implementation) — a real Postgres-backed concurrency test proves step 5 closes the race: two threads calling the capture path with the same `event_id` concurrently must produce exactly one `CAPTURED` transition and exactly one publish. Existing Mockito-mocked `PaymentServiceTest` tests cannot prove this (mocking the repository can't exercise a real unique constraint), so this is a new, separate test class.
9. **`spring.jpa.hibernate.ddl-auto=validate`** — Flyway owns schema, Hibernate only validates against it. **`spring.flyway.enabled=true`.**
10. **Event contract is unchanged** — `PaymentCapturedEvent(bookingRef, providerPaymentId, amountPaise)` stays byte-identical; trip-service's and booking-service's consumers need no change. G1 (`feePaise` always 0) and G2 (whatever the existing second guarantee is) are preserved and additionally enforced at the schema level (`NOT NULL DEFAULT 0`).
11. **Firestore is untouched everywhere else** — booking/leg/trip/catalog documents stay exactly as they are.

## Architecture

```
payment-service
  ├── entity/            Payment, ProcessedWebhookEvent, Refund, Settlement (JPA @Entity)
  ├── repository/        PaymentRepository, ProcessedWebhookEventRepository,
  │                       RefundRepository, SettlementRepository (Spring Data JpaRepository)
  ├── service/
  │     PaymentService          — synchronous now; createOrder (unchanged logic),
  │                                applyWebhook (rewritten, atomic capture per Decision 5),
  │                                getStatus, refund (writes a Refund row)
  │     ReconciliationService   — new; aggregate queries for the admin endpoint
  ├── controller/
  │     PaymentController       — unchanged endpoints, synchronous return types
  │     ReconciliationController — new, ROLE_ADMIN
  └── resources/
        application.properties  — spring.datasource.*, spring.jpa.*, spring.flyway.*
        db/migration/V1__money_ledger.sql
```

Cloud SQL: one Postgres 15 instance (`asia-south2`), one database (`t2g_payments`), one app user, reached only from payment-service via the socket factory. No other service touches it.

## Schema (`V1__money_ledger.sql`)

- `payments`: `id UUID PK DEFAULT gen_random_uuid()`, `booking_ref`, `owner_user_id`, `status`, `amount_paise BIGINT`, `fee_paise BIGINT NOT NULL DEFAULT 0` (G1), `method`, `provider_order_id`, `provider_payment_id`, `quote_token_validated BOOL`, `created_at`, `updated_at`; `UNIQUE(provider_order_id)`; `UNIQUE(provider_payment_id) WHERE provider_payment_id IS NOT NULL`; indexes on `booking_ref` and `owner_user_id`.
- `processed_webhook_events`: `event_id TEXT PK`, `processed_at` — the PK **is** the atomic dedupe.
- `refunds`: `id UUID PK`, `payment_id FK -> payments`, `provider_refund_id`, `amount_paise BIGINT`, `status`, `created_at`.
- `settlements`: `id UUID PK`, `payment_id FK -> payments`, `provider_settlement_id`, `amount_paise BIGINT`, `settled_at`, `status`.

## Data flow — webhook capture (the point of this ticket)

```
Razorpay webhook -> RazorpayProvider.verifyAndParse (unchanged, HMAC check)
  -> PaymentService.applyWebhook(eventId, providerPaymentId, ...)
       @Transactional:
         1. INSERT processed_webhook_events(event_id)   -- PK violation = duplicate, no-op
         2. UPDATE payments SET status='CAPTURED', provider_payment_id=?, updated_at=now()
              WHERE id=? AND status='CREATED'            -- 0 rows affected = no-op
         3. commit
       (transaction boundary ends here)
       4. rabbitTemplate.convertAndSend("trip.exchange", "payment.captured", event)  -- after commit
```

Refund follows the same shape: `@Transactional` write of a `Refund` row + conditional status update on `payments`, publish after commit.

## Config / infra

- `payment-service/pom.xml`: add `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `flyway-core`, `com.google.cloud.sql:postgres-socket-factory`; remove `spring-cloud-gcp-starter-data-firestore`. Test scope: `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`.
- `application.properties`: `spring.datasource.url=jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${INSTANCE_CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory`, `spring.datasource.username=${DB_USERNAME}`, `spring.datasource.password=${DB_PASSWORD}` — all required, no in-source defaults (A2). `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`.
- Terraform: new `google_sql_database_instance` (Postgres 15, `asia-south2`, smallest viable tier), `google_sql_database` (`t2g_payments`), `google_sql_user` (password from a Terraform variable, no committed default), and a `google_project_iam_member` granting `t2g-payment` `roles/cloudsql.client`, following `iam.tf`'s existing per-SA pattern.
- `backend-deploy.yml`: add `--add-cloudsql-instances=${INSTANCE_CONNECTION_NAME}` to payment-service's deploy step only, plus `DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`/`INSTANCE_CONNECTION_NAME` env vars scoped to payment-service via the existing per-service case-block pattern.

## Error handling

- Duplicate webhook delivery: step 1's PK violation is caught, logged at INFO (expected, not an error), method returns normally — no exception surfaces to the caller, matching current behavior.
- Race where two threads both pass step 1 for *different* event IDs but the same payment (e.g. a provider retry with a new event id for an already-captured payment): step 2's `WHERE status='CREATED'` guards this — the second thread's UPDATE affects 0 rows, logged and no-op, no duplicate publish.
- Publish failure after commit: logged as an error (money state is correct in the DB; the event is lost). This is the exact gap P1.4's transactional outbox exists to close — out of scope here, called out explicitly in hand-back.
- Refund/settlement writes follow the same transactional-then-publish-after-commit shape.

## Testing

- Existing `PaymentServiceTest` (Mockito) rewritten for the new synchronous JPA repository shape (`List`/`Optional` instead of `Flux`/`.block()`); same scenarios, same assertions, updated mocking style only.
- New Testcontainers integration test (real Postgres 15 container): two concurrent threads call `applyWebhook` with the same `event_id` and same target payment — assert exactly one `CAPTURED` row, exactly one call to the message publisher (a spy/mock at the publish boundary, since the container doesn't run RabbitMQ).
- A second Testcontainers case: two different `event_id`s targeting the same already-`CAPTURED` payment — assert the second is a no-op (0 rows affected on its UPDATE), no double-publish.
- Reconciliation endpoint: standard `@WebMvcTest`/`MockMvc` tests for ROLE_ADMIN gating and aggregate correctness against seeded rows.

## Acceptance criteria

1. `payment-service` has no remaining Firestore dependency; `grep -r FirestoreReactiveRepository microservices/payment-service` returns nothing.
2. Flyway migration(s) exist under `payment-service/src/main/resources/db/migration/`, creating `payments`, `processed_webhook_events`, `refunds`, `settlements` with the constraints listed above.
3. `UNIQUE` constraints on `provider_order_id`, `provider_payment_id`, and `processed_webhook_events.event_id` (PK) all present and grep-verifiable in the migration SQL.
4. `t2g-payment` has `roles/cloudsql.client` in Terraform (`terraform plan` shows the binding; not yet applied — manual `terraform apply` remains a hand-back item per this session's existing convention).
5. The Testcontainers concurrency test passes, demonstrating exactly-once capture under concurrent duplicate delivery.
6. `PaymentCapturedEvent`'s shape is unchanged — trip-service and booking-service consumers require no code changes.
7. `spring.jpa.hibernate.ddl-auto=validate` and `spring.flyway.enabled=true` are set; `mvnw clean verify` passes locally with a real (Testcontainers) Postgres.
8. Reconciliation endpoint exists, `ROLE_ADMIN`-gated, returning captured/refunded/settled totals and an unsettled-captures list.

## Out of scope (explicitly deferred)

- Transactional outbox / relay (P1.4) — publish-after-commit here is a real improvement over the old race but is not itself durable against a post-commit crash; P1.4 closes that gap.
- DLQ / bounded retry on consumers (P1.4).
- Leg-booking notifications (P1.5, brief not yet provided).
- Actually running `terraform apply` and rotating secrets — manual ops, flagged in hand-back, consistent with F1/F2's hand-back.
- Any change to booking-service, trip-service, or any other service's Firestore usage.
