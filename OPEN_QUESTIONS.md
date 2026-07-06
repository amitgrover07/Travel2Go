# Open Questions

External contracts and decisions this build stubbed behind an interface rather than guessing. Resolve before the corresponding phase ships to production.

## Payments (Phase 0, `payment-service`)
- **UPI provider**: Razorpay vs. Cashfree not yet chosen; no API keys exist in this repo. `PaymentProvider` is implemented today by `SandboxUpiProvider`, which always returns `SUCCESS` with a synthetic `providerRef`. Swap in a real implementation behind the same interface once a provider + keys are confirmed.
- **Quote-token secret**: `quote.token.secret` currently ships a placeholder default in `application.properties`. Must be set to a real secret via `QUOTE_TOKEN_SECRET` in the deploy workflow before this reaches production traffic that isn't sandboxed.

## Infrastructure (Phase 0 architecture report, carried forward)
- **Redis / RabbitMQ in production**: `.github/workflows/backend-deploy.yml` injects `SPRING_REDIS_HOST` / `SPRING_RABBITMQ_HOST` from GitHub secrets rather than the docker-compose hostnames, implying managed instances (e.g. Memorystore, CloudAMQP) — the exact provider is not declared anywhere in this repository.
- **`backend/` monolith retirement**: confirmed unreferenced by CI, docker-compose, and the frontend, and deleted in Phase 0 Task 1. If anyone was still hitting it directly (bypassing the gateway), that integration is now broken — watch for reports.

## Security follow-ups (Phase 0, found during build)

- **`booking-service`'s `POST /api/leg-bookings` has no quote-token validation, no trip-ownership check, and marks bookings `CONFIRMED` with no payment-service charge step.** It is not reachable through `api-gateway` today (gateway route `[3]` only matches `/api/bookings/**`, not `/api/leg-bookings/**`), so the only current barrier is network-level isolation between services — there is no defense in the endpoint itself. Before this reaches production traffic, resolve: (1) where quote-token validation should live (a shared `QuoteTokenService`, or a synchronous call to `payment-service`), and (2) whether a leg booking should only be marked `CONFIRMED` after `payment-service.charge(...)` succeeds, rather than immediately on request. Deferred by explicit human decision during Phase 0 build — not yet fixed.
- **`trip-service` initially shipped (Task 3) with no per-resource authorization** — any authenticated user could read or mutate any other user's `Trip` by guessing/enumerating a `tripId`. This was found by automated security review and fixed within Phase 0 (commit `67bb60f`): `TripController`/`TripService` now bind `ownerUserId` on trip creation and reject non-owner reads/writes with `AccessDeniedException`. No action needed — noted here as a build-history record, since the fix landed as an unplanned addition to this plan rather than as a numbered task.

## Phase 1+ (from the PRD, not yet started)
- IRCTC authorised-partner access for `inventory-service`'s `RailAdapter` — available, or does rail launch management-only over public data first?
- WhatsApp Business API BSP (Gupshup / Meta direct / other) and template approval status, for `notification-service`'s WhatsApp channel.
- Firestore's fit for itinerary/monitoring queries at scale (e.g. "all trips with a leg delayed in the next 6h") — needs validation before `journey-monitor-function` (Phase 2) scales.
