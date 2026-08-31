# P1.1 — Payment provider + payment-gated confirmation (design)

Status: approved for planning
Scope: `microservices/payment-service` only, plus `microservices/api-gateway` routing/env for the webhook path. Does not touch booking-service validation (P1.2), the Postgres ledger (P1.3), or the saga (P1.4).

## Source briefs

- `P1.0-overview.md` — invariants (G1: `feePaise=0`, G2: quote-token gate), compliance frame (licensed PA, escrow, PCI SAQ-A, `asia-south2`), sequencing.
- `P1.1-payment-provider-gated-confirmation.md` — the detailed brief this design implements.

## Current state (verified against the repo, 2026-08-31)

- `PaymentController.charge()` → `PaymentService.charge()` → `SandboxUpiProvider.charge()` — synchronous, always `SUCCESS`. No async order/webhook flow.
- `PaymentProvider` interface: single method `PaymentResult charge(String reference, long amountPaise, String method)`.
- `Payment` model lives in `common-models` (`com.travel2go.backend.model.Payment`), Firestore-backed via `FirestoreReactiveRepository`. Fields: `id, bookingRef, method, status (String), amountPaise, feePaise, providerRef, quoteTokenValidated, createdAt`.
- `QuoteTokenService` and `JwtUtil` already unified in `platform-security` (A1) — `QuoteTokenService.isValid(token, legId, pricePaise)` is the existing G2 gate, reused as-is.
- `payment-service` has **no RabbitMQ dependency or config today** — unlike trip-service/booking-service. Must be added from scratch.
- `payment-service` `SecurityConfig` requires JWT on `.anyRequest().authenticated()` — would reject Razorpay's webhook call, which carries no JWT.
- `payment-service` Cloud Run deploy flag is `--no-allow-unauthenticated` (A4 isolation); IAM invoker is service-level, not path-level, so a webhook call from Razorpay (no GCP identity) cannot reach it directly.
- No backend service calls `POST /api/payments` today (no Feign client anywhere in the repo) — safe to replace the synchronous endpoint outright.
- Admin-gating convention already established elsewhere: `.requestMatchers(path).hasAuthority("ROLE_ADMIN")` (see booking-service `SecurityConfig`).
- `trip-service`'s `TripEventPublisher` is the reuse template for event emission: `rabbitTemplate.convertAndSend("trip.exchange", routingKey, payload)`, with a `TopicExchange` bean declaring `trip.exchange`.

## Decisions

1. **Razorpay client:** official `com.razorpay:razorpay-java` SDK (order creation + `Utils.verifyWebhookSignature`), not a hand-rolled HTTP+HMAC client.
2. **Idempotency store:** new Firestore collection `processed_webhook_events` (own `FirestoreReactiveRepository`), keyed by provider event id — same pattern as `Payment` itself, not a field bolted onto `Payment`.
3. **RabbitMQ:** add `spring-boot-starter-amqp` to `payment-service`, a `RabbitMQConfig` that declares `trip.exchange` as a `TopicExchange` (idempotent re-declare — trip-service already declares the same exchange/type), and a `PaymentEventPublisher` mirroring `TripEventPublisher`. Scope `SPRING_RABBITMQ_*` env vars to `payment-service` in `backend-deploy.yml`'s existing case block (A3 pattern).
4. **Webhook public exposure:** add a gateway route for `/api/payments/webhook` (gateway is public, already holds `run.invoker` on payment-service per `infra/terraform/run-invoker.tf`). `payment-service` itself stays `--no-allow-unauthenticated`. Inside `payment-service`'s own `SecurityConfig`, `permitAll()` only that one path; auth is via HMAC signature verification in the handler, not JWT.
5. **Old sync endpoint:** `POST /api/payments` (`PaymentController.charge`, `PaymentService.charge`, `ChargeRequest`, `SandboxUpiProvider`, old `PaymentProvider.charge`) is fully replaced, not kept alongside the new flow. Flag for the user: any frontend caller of the old shape needs a follow-up update — out of scope here.
6. **Sandbox provider fidelity:** `SandboxPaymentProvider.verifyAndParse` performs a real HMAC-SHA256 check against a local test secret (same shape as `RazorpayProvider`), so the signature-rejection path is unit-testable without live Razorpay credentials.
7. **`Payment.status` type:** stays `String` (matches existing repo convention), documented allowed values: `CREATED | CAPTURED | FAILED | REJECTED | REFUNDED`.
8. **Escrow/Route (brief §7):** dashboard-side Razorpay account configuration, not application code. No implementation task here — recorded as an assumption to verify against the Razorpay account setup.

## Architecture

```
PaymentController
  POST /api/payments/order         -> PaymentService.createOrder(...)
  POST /api/payments/webhook       -> PaymentService.applyWebhook(rawBody, headers)
  GET  /api/payments/{bookingRef}  -> PaymentService.getStatus(...)
  POST /api/payments/{bookingRef}/refund -> PaymentService.refund(...)   [ROLE_ADMIN]

PaymentProvider (interface)
  CreatedOrder createOrder(reference, amountPaise, method)
  WebhookEvent verifyAndParse(rawBody, headers)   // throws/returns signature-invalid outcome
  RefundResult refund(providerPaymentId, amountPaise)

  RazorpayProvider   @ConditionalOnProperty(payment.provider=razorpay, default)  — wraps razorpay-java
  SandboxPaymentProvider @ConditionalOnProperty(payment.provider=sandbox)        — real HMAC, local secret

PaymentEventPublisher (new, mirrors TripEventPublisher)
  publish(routingKey, payload) -> rabbitTemplate.convertAndSend("trip.exchange", routingKey, payload)

ProcessedWebhookEventRepository (new Firestore collection)
```

## Data flow

1. **Create order** — `POST /api/payments/order { bookingRef, amountPaise, method, quoteToken }`:
   - `quoteTokenService.isValid(quoteToken, bookingRef, amountPaise)` (G2). Invalid → persist `Payment{status=REJECTED, quoteTokenValidated=false}`, `402`.
   - `feePaise = 0L` always (G1).
   - `provider.createOrder(...)` → persist `Payment{status=CREATED, providerRef=orderId, quoteTokenValidated=true}`.
   - Return checkout params (order id, PA key id, amount, currency) — never a secret.
2. **Client pays** on PA hosted checkout — out of scope (frontend concern).
3. **Webhook** — `POST /api/payments/webhook`, raw `byte[]` body (registered via the default `ByteArrayHttpMessageConverter`, not `@RequestBody` DTO — avoids re-serializing before HMAC verification, which would break the signature):
   - `provider.verifyAndParse(rawBody, headers)`. Invalid signature → `400`, no state change, no DB write.
   - Look up `ProcessedWebhookEvent` by the event's provider id. Already processed → `200`, no-op.
   - Amount in webhook must equal the stored order's `amountPaise`; mismatch → do not capture, log as alert-worthy, `200` (verified+understood, just rejected on business grounds) — no state change.
   - Apply only a **legal** transition: `CREATED→CAPTURED` or `CREATED→FAILED`. A `failed` arriving after `captured` is ignored (already terminal).
   - On `CREATED→CAPTURED`: persist the transition, save the `ProcessedWebhookEvent`, publish `payment.captured` `{bookingRef, providerPaymentId, amountPaise}` to `trip.exchange`. This is the only place a downstream confirmation signal is emitted.
   - Always `200` for a verified, understood webhook (including duplicates); non-2xx only for signature failure.
4. **Status read** — `GET /api/payments/{bookingRef}` → current `Payment`.
5. **Refund** — `POST /api/payments/{bookingRef}/refund`, `ROLE_ADMIN`: `provider.refund(providerPaymentId, amountPaise)` → `CAPTURED→REFUNDED`, publish `payment.refunded`. Second call on an already-`REFUNDED` payment is a no-op returning the existing state.

## Config & secrets (A2/A3 pattern)

- `payment-service/application.properties`: `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` — `${VAR}` with no in-source default, `@PostConstruct` fail-fast if missing/too short (mirrors `JwtUtil`/`QuoteTokenService`).
- `backend-deploy.yml` "Build service-scoped env vars" step: add a `payment-service)` case emitting the three `RAZORPAY_*` vars (secret + webhook secret to payment-service only; `RAZORPAY_KEY_ID` may also reach api-gateway/frontend later as the public key, not addressed here). Add `payment-service` to the existing `SPRING_RABBITMQ_*` case alongside `booking-service|trip-service|reactive-booking-function`.
- `api-gateway`: new route entry `spring.cloud.gateway.routes[9].id=payment-webhook`, `uri=${PAYMENT_SERVICE_URL}`, `predicates[0]=Path=/api/payments/webhook`. Add `PAYMENT_SERVICE_URL` to gateway's `CR_ENV_VARS` case and to `DownstreamUrlValidator`'s required-URL list (B6). `payment-service` deploy flags (`--no-allow-unauthenticated`) unchanged.
- `payment-service/SecurityConfig`: `.requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()`, `.requestMatchers(HttpMethod.POST, "/api/payments/*/refund").hasAuthority("ROLE_ADMIN")`, rest stays `authenticated()`.

## Error handling

| Case | Response | State change |
|---|---|---|
| Invalid webhook signature | `400` | none |
| Duplicate captured event | `200` | none (dedup hit) |
| Amount mismatch | `200` | none, logged as alert |
| `failed` after `captured` | `200` | none (illegal transition ignored) |
| Quote-token invalid at order creation | `402` | `Payment{REJECTED}` persisted |
| Refund on already-`REFUNDED` | `200` | none, returns existing state |

## Testing

- `PaymentServiceTest`: replace the old `charge()` tests with `createOrder` (quote-token valid/invalid), `applyWebhook` (captured, failed, duplicate delivery, amount mismatch, illegal-transition-ignored), `refund` (success, idempotent no-op).
- `SandboxPaymentProviderTest`: round-trip `createOrder` → sign a payload → `verifyAndParse` succeeds; tampered signature → rejected. Mirrors the existing `QuoteTokenServiceTest` style.
- Manual verification commands from the brief §9 (grep for no-always-success on the money path, signature/HMAC presence, idempotency references, secret scoping) run before hand-back.

## Acceptance criteria

Per brief §8, all 9 items apply unchanged: no always-success provider on the money path; order creation never confirms; signature verified over raw body; duplicate webhook → exactly one capture/one event; failed webhook stays recoverable; G1/G2 preserved; `RAZORPAY_*` secrets fail-fast with no defaults, scoped to payment-service; refund is idempotent; funds never custodied (escrow/Route account assumption noted above).

## Out of scope (explicitly deferred)

- Frontend changes to call the new `/order` + poll `/{bookingRef}` shape instead of the old synchronous `POST /api/payments`.
- P1.2 (booking-service independent re-validation), P1.3 (Postgres ledger), P1.4 (saga/outbox).
- Razorpay Route/escrow account provisioning (dashboard-side, not code).
