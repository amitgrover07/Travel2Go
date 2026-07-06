# Migrations

Log of schema/collection changes. Firestore has no formal migration tooling, so this file is the audit trail.

## 2026-07-06 — Phase 0: Foundations

**New Firestore collections** (all in the `travel2go-db` database, created on first write — no manual provisioning needed):
- `trips` — `Trip` model, owned by `trip-service`.
- `legs` — `Leg` model, owned by `trip-service`.
- `travellers` — `Traveller` model, owned by `identity-service` in a later phase; collection reserved now.
- `traveller_groups` — `TravellerGroup` model, same as above.
- `payments` — `Payment` model, owned by `payment-service`.

**Modified collection**: `bookings` (owned by `booking-service`). Added five nullable fields to the `Booking` document: `tripId`, `legId`, `quoteToken`, `amountPaise`, `feePaise`. Existing documents are unaffected — Firestore has no fixed schema, and the legacy `POST /api/bookings` (package-booking) flow never sets these fields, so they simply remain absent on old and legacy-flow documents. Only documents created via the new `POST /api/leg-bookings` endpoint populate them.

**Deleted**: the `backend/` monolith (Spring Boot app + all its source) and six duplicated `tree.txt` dependency dumps under `microservices/*/`.
