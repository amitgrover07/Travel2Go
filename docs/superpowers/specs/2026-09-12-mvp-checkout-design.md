# MVP checkout: make the money loop reachable end-to-end (design)

Status: approved for planning
Scope: three small, additive backend completions (sign the quote token server-side; route `/api/payments/**` through the gateway; send a real leg-booking confirmation notification) plus a new frontend checkout flow (trip/leg/booking/payment API calls, Razorpay hosted checkout, status-poll-driven confirmation UI). Ops switches (live Razorpay account, webhook URL, `terraform apply`, secret rotation) are manual and out of this implementation's scope. Does not touch or rewrite any existing endpoint, model, or consumer beyond the three named completions.

## Source

`MVP-CHECKOUT-BRIEF.md`, `main` @ `3a173e8` (has P1.1–P1.4, F1/F2, A1–B6 merged).

## Current state (verified against the repo, 2026-09-12)

- `QuoteTokenService.issue(legId, price)` exists in `platform-security` but is called **only from its own unit test** (`QuoteTokenServiceTest`) — grepped the whole `microservices/` tree, zero calls from application code. `TripService.addLeg` builds a `Leg` with `.quoteToken(request.getQuoteToken())` — a client-supplied string that can never be a token a real client could have legitimately produced (it's HMAC-signed with a server-only secret over a server-generated `legId`). `bookLeg`'s `quoteTokenService.isValid(...)` check can therefore never pass for a real caller today — the brief's diagnosis is exactly right.
- `api-gateway/application.properties` has 10 routes (indices 0–9); only `routes[9]` touches payment-service, and it's an exact-path match on `/api/payments/webhook` only. The file's own comment at line 51–55 confirms: *"Order/status/refund calls are not gateway-routed yet."* `DownstreamUrlValidator` already requires `PAYMENT_SERVICE_URL` to be a valid URL at startup (line 33/39) — no change needed there for this ticket.
- `booking-service`'s `LegConfirmedConsumer.onLegConfirmed` confirms the `Booking` then only logs `"...leg-booking confirmation notifications not yet implemented..."` (line 62–64). `NotificationClient` has exactly one method, `sendBookingConfirmation(NotificationRequest)`, shaped entirely around legacy **package** bookings (`BookingRequest`, `HolidayPackage`, `GlobalSettings`, `Booking`) — none of which fit a leg booking. `NotificationClientFallbackFactory` implements the client via a single-method lambda (`request -> log.warn(...)`), which only works because the interface currently has exactly one method.
- **A gap the brief did not account for, discovered during exploration:** `LegBookingRequest` (the DTO `trip-service` sends to `booking-service` to create a leg booking) has exactly four fields — `tripId`, `legId`, `quoteToken`, `amountPaise`. No email, no phone, anywhere in the leg-booking creation path. `Booking` (the Firestore model) *does* have `firstName`/`lastName`/`email`/`phone` fields, but they're only ever populated by the **legacy package-booking** flow — `LegBookingService.createLegBooking` never sets them. There is currently no traveller contact information anywhere for a leg booking to notify. `booking-service` has no existing Feign client to identity-service for user-profile lookup (`SettingsClient` fetches `GlobalSettings`, unrelated). **User-approved fix:** the frontend passes email/phone at leg-booking-creation time, sourced from the already-logged-in user's own JWT.
- `frontend/src/services/api.js` is a bare axios instance (baseURL resolution + auth-token interceptor + 401/403 logout interceptor) — zero domain-specific API call functions exist for trips, legs, bookings, or payments.
- `frontend/src/App.jsx` uses `react-router-dom` v7, a `ProtectedRoute` wrapper keyed on `localStorage.getItem('token')`, and a `MainLayout` wrapper for content pages. `Login.jsx` already has a `getTokenPayload(token)` helper that base64-decodes the JWT payload client-side (used today to detect `role === 'ADMIN'`) — the same technique will supply the checkout page's pre-filled email.
- `frontend/package.json`: React 19, react-router-dom 7, axios, Tailwind 4, Vite 8. **No test framework configured** (no vitest/jest, no `test` script).
- `payment-service`'s `SecurityConfig` confirms the brief's stated auth model exactly: `POST /api/payments/webhook` is `permitAll()`; `POST /api/payments/*/refund` and `GET /api/payments/reconciliation` require `ROLE_ADMIN`; everything else (`order`, `GET /{bookingRef}`) falls through to `.anyRequest().authenticated()` — a valid JWT, checked by `ResourceServerJwtAuthenticationFilter`.

## Decisions

1. **1A signs the token immediately after the first `Leg` save, then re-saves.** `pricePaise` comes from `AddLegRequest` unchanged (the price the user selected); any client-supplied `quoteToken` on the request is accepted into the DTO (so existing callers don't break at the wire level) but is **never used** — the server always overwrites it with its own signed token before returning.
2. **1B adds one new route (`routes[10]`), keeps `routes[9]` (webhook) as-is.** Both point at `${PAYMENT_SERVICE_URL}`; Spring Cloud Gateway matches routes in declaration order, but since both destinations are identical there's no behavioral risk either way — kept separate per the brief's own instruction to preserve the existing webhook route untouched.
3. **1C's contact-info gap is closed by the frontend, not a new backend user-lookup.** `LegBookingRequest` gains two new **optional** fields, `email` and `phone`; `LegBookingService.createLegBooking` copies them onto the `Booking` it builds (both the `PENDING` and `REJECTED` branches, for consistency, though only a confirmed booking ever gets notified). No new Feign client, no identity-service dependency added to booking-service.
   - **Wire-level consequence, found during self-review:** `POST /api/trips/{tripId}/legs/{legId}/book` (the endpoint the frontend actually calls to trigger booking) currently takes **no request body at all** — `TripController`'s handler and `TripService.bookLeg(tripId, legId, requestingUserId)` have no parameter to carry email/phone through to the `LegBookingRequest` it builds. This ticket adds a small new request body DTO, `BookLegRequest{email, phone}` (both optional strings), to that endpoint, and threads both values through `TripService.bookLeg`'s new parameter into the `LegBookingRequest.builder()` call. This is the missing link the brief's own frontend table didn't show (it listed `/book`'s body as `—`).
4. **`NotificationClientFallbackFactory` becomes a small class, not a lambda.** Adding a second method to `NotificationClient` breaks its current single-method-lambda implementation; the factory becomes `new NotificationClient() { ... }` (or a named inner class) with both methods independently logging-and-swallowing on failure — same B5 philosophy, unchanged for the existing method.
5. **The new leg-booking confirmation email is plain-text, no PDF, no GCS archival.** The existing `sendBookingConfirmation` attaches a generated PDF and archives the raw `.eml` to GCS — both are package-booking-specific niceties not required for MVP functionality. The new `EmailService.sendLegBookingConfirmation(...)` sends a plain confirmation (traveller name if available, leg type, amount, confirmation reference) via `SimpleMailMessage`, mirroring `sendVerificationCode`'s simplicity rather than `sendBookingConfirmation`'s complexity.
6. **No new dedupe flag on `Booking`.** The consumer's existing `if (!"PENDING".equals(booking.getStatus()))` guard already makes the notification-send code unreachable a second time for the same booking — a redelivered `leg.confirmed` finds `status == CONFIRMED` and returns before ever reaching the notification call. This satisfies the brief's "idempotent / dedupe-keyed" requirement without new persisted state.
7. **A failed notification never fails the message.** The `tripEventPublisher`-style pattern from P1.4 doesn't apply here (that was Publish-failure inside a producer); this is a Feign call whose failure is already absorbed by the existing B5 circuit-breaker + fallback-factory swallow. No `try/catch` needed in `LegConfirmedConsumer` itself — the Feign client's own fallback handles it.
8. **Frontend contact info comes from the decoded JWT, not a new profile-fetch call.** `Checkout.jsx` reuses `Login.jsx`'s `getTokenPayload` technique to read `sub` (email) out of the stored token for a pre-filled, editable email field; phone is a plain required input (not present in the JWT payload shown in `Login.jsx`'s admin-bypass example).
9. **Frontend verification is manual (dev server + browser), not automated.** The repo has no frontend test framework; introducing one is out of scope for an "additive small completions" MVP ticket. This task's own acceptance is exercising the real flow in a running dev server, per this session's standing instruction for UI changes.
10. **Ops items (§3 of the brief) are not implementation tasks.** Live Razorpay account, webhook URL registration, `terraform apply`, secret rotation are hand-back items, listed but not executed by this plan.

## Architecture

```
1A  trip-service/TripService.addLeg
      legRepository.save(leg)              [unchanged position]
      quoteTokenService.issue(legId, price) [NEW]
      leg.setQuoteToken(signedToken)        [NEW - overwrites client value]
      legRepository.save(leg)               [NEW second save]

1B  api-gateway/application.properties
      routes[9]  = payment-webhook   (unchanged, exact path /api/payments/webhook)
      routes[10] = payment-service   (NEW, Path=/api/payments/**)

1C  trip-service                    booking-service                        notification-service
      BookLegRequest{email,phone}    LegBookingRequest gains email/phone     EmailService.sendLegBookingConfirmation (NEW)
      (NEW request body on /book)    LegBookingService copies onto Booking   NotificationController's new endpoint (NEW)
      TripService.bookLeg threads    LegConfirmedConsumer calls new
      email/phone through        ->  client method                      ->  NotificationClient.sendLegBookingConfirmation (NEW)
                                      NotificationClientFallbackFactory (restructured, both methods)

Frontend
      api.js: createTrip, addLeg, bookLeg, createPaymentOrder, getPaymentStatus  (NEW functions)
      Checkout.jsx: contact form -> bookLeg -> createPaymentOrder -> Razorpay -> poll getPaymentStatus
      App.jsx: new protected routes for the checkout flow
```

## Data flow — checkout, end to end

1. User is on a leg-selection UI (existing or minimally new — see §UI below), has already created a `Trip` and added a `Leg` (`POST /api/trips`, `POST /api/trips/{tripId}/legs` — the latter now returns a server-signed `quoteToken`).
2. User clicks "Book" → `Checkout.jsx` shows a contact-confirmation step (email pre-filled from the decoded JWT, phone required) → on submit, calls `POST /api/trips/{tripId}/legs/{legId}/book` with body `{email, phone}` (the new `BookLegRequest`) — trip-service validates the signed token, calls `booking-service` to create the `Booking` PENDING, threading email/phone through into `LegBookingRequest`.
3. `Checkout.jsx` then calls `POST /api/payments/order` with `{bookingRef: legId, amountPaise: leg.pricePaise, method: "razorpay", quoteToken: leg.quoteToken}` — payment-service independently re-validates the same token (P1.2's independent-validation guarantee, unchanged) and returns a `Payment` with `status: "CREATED"` and `providerRef` (the Razorpay order id).
4. Razorpay's hosted checkout opens (`key = VITE_RAZORPAY_KEY_ID`, `order_id = payment.providerRef`, `amount = payment.amountPaise`). User completes payment in the Razorpay modal.
5. Regardless of what the Razorpay client-side callback reports, `Checkout.jsx` polls `GET /api/payments/{legId}` (bounded interval, bounded timeout) until `status === "CAPTURED"` — this is payment-service's own state, only ever set by the real webhook (P1.1's payment-gated confirmation, unchanged).
6. Once payment-service's webhook has processed the capture: the existing P1.4 chain fires unchanged — outbox → relay → `payment.captured` → trip-service confirms `Leg` + publishes `leg.confirmed` → booking-service confirms `Booking` **and now also sends the confirmation email** (1C).
7. `Checkout.jsx`'s poll sees `CAPTURED` and shows the confirmation screen. If the poll times out or sees `FAILED`, it shows a recoverable retry state — never a fabricated success.

## Error handling

- **1A**: if `quoteTokenService.issue(...)` throws (misconfigured `QUOTE_TOKEN_SECRET` — already fail-fast at startup per A3, so this shouldn't happen at runtime), `addLeg` fails loudly (500) rather than silently returning an unsigned leg — no `try/catch` added, matching this codebase's existing pattern of not swallowing unexpected exceptions in write paths.
- **1B**: no new error handling — routing is declarative config; payment-service's own `SecurityConfig`/`GlobalExceptionHandler` (if any) already governs the response shapes for `order`/`refund`/status once reachable.
- **1C**: a notification-service outage or Feign failure is already absorbed by the existing B5 circuit breaker + fallback factory — booking confirmation is unaffected either way, matching the brief's explicit "don't let a notification failure roll back the confirmation."
- **Frontend**: `createPaymentOrder` returning 402 (REJECTED, invalid token) or 409 (conflict, already claimed by another user) are shown as distinct, non-retryable error states, not generic failures. The status poll has a bounded timeout (e.g. 90s) after which it shows "still processing, check back shortly" rather than spinning forever.

## Testing

- **1A**: `TripServiceTest` (new or extended) asserts `addLeg` returns a `Leg` whose `quoteToken` is signed such that `quoteTokenService.isValid(token, legId, price)` is true, and that a client-supplied `quoteToken` in the request is ignored (the returned token differs from whatever was submitted).
- **1B**: no meaningful unit test for declarative Spring Cloud Gateway route config in this codebase's existing pattern (no route-level tests exist for any of the other 9 routes either) — verified by the plan's grep check plus `ApiGatewayApplicationTests`' existing context-load test still passing (confirms the new route property doesn't break startup), plus a manual `curl` verification note in the task.
- **1C**: `LegConfirmedConsumerTest` gets a new test asserting `notificationClient.sendLegBookingConfirmation(...)` is called with the right payload after a successful confirm, and that it's *not* called on a duplicate/no-op delivery (status guard still gates it, same as before). `EmailServiceTest`/`NotificationControllerTest` (new, mirroring existing patterns in notification-service) cover the new send path. `LegBookingServiceTest` gets a new test confirming email/phone flow through onto the saved `Booking`.
- **Frontend**: no automated tests added (Decision 9) — verified by running `npm run dev` and exercising the real flow against a locally running backend (trip-service, booking-service, payment-service, api-gateway, RabbitMQ, Postgres) in a browser, confirming the confirmation screen only appears after a real webhook-driven `CAPTURED` status, not on the Razorpay modal's own close/success callback.

## Acceptance criteria

(Copied from the brief, all in scope for this design.)

1. `addLeg` returns a `Leg` with a server-signed `quoteToken`; `bookLeg` validation passes for a real client; nothing else signs tokens client-side.
2. The gateway routes `/api/payments/order`, `/api/payments/{ref}`, and refund to payment-service; webhook still works; auth unchanged.
3. A real user can, end-to-end in the browser: create trip → add leg → book → Razorpay hosted checkout → see the booking become CONFIRMED only after the webhook capture (via status poll), and receive a confirmation notification.
4. An admin can refund a captured payment (already true today via the existing `POST /api/payments/{bookingRef}/refund` — this ticket only makes it *reachable* from the frontend for a logged-in admin; no new admin UI is required by the brief, so this criterion is satisfied by 1B alone plus the pre-existing endpoint).
5. No existing endpoint, model, or feature is deleted or rewritten beyond §1's three additive completions.
6. Invariants intact: `feePaise=0` (G1), quote-token gate (G2, now genuinely enforced per 1A), payment-gated confirmation, owner-scoped reads, idempotent consumers, RabbitMQ `trustedPackages`.
7. CI (`backend-ci.yml`) green for all affected services.

## Out of scope (explicitly deferred)

- A dedicated admin refund UI — criterion 4 is satisfied by making the endpoint reachable; no frontend admin screen is built here.
- Booking-service → identity-service profile lookup (Decision 3's rejected alternative) — a future ticket if the frontend-supplied-contact-info approach proves insufficient.
- PDF attachment / GCS `.eml` archival for the new leg-booking confirmation email (Decision 5).
- Automated frontend tests (Decision 9).
- Everything the brief's own §7 hand-back lists as future phases: composable multi-leg builder UI, real supplier inventory, further outbox durability work for trip-service, partial refunds, journey monitoring, membership/referral/reviews, AI layer.
- All of §3's ops switches (manual, not code).
