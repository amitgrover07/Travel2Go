# Travel2Go — Complete Project Documentation

Consolidated from all repo markdown sources: `MIGRATIONS.md`, `OPEN_QUESTIONS.md`, `PROJECT_OVERVIEW.md`, `docs/superpowers/plans/2026-07-06-phase0-foundations.md` (Phase 0 implementation plan), `frontend/README.md`, `scratch/step_168.md`, plus direct source-tree verification (current as of 2026-08-12, git branch `main`). Where the Phase 0 plan and current code diverge, current code wins and is noted.

---

## 1. What the project is

Travel package booking platform. React (Vite) frontend talking through an `api-gateway` to a fleet of Spring Boot microservices, backed by Firestore (`travel2go-db`, GCP project `travel2go-495007`, region `asia-south2`). Handles holiday/custom package browsing and booking, a newer trip/leg-based journey model with priced quotes and payment, admin tooling (packages, allocation rules, users, leads), auth (JWT + OAuth2 + OTP), media upload to GCS, and notifications (email/SMS). Deploys to Cloud Run via GitHub Actions.

A prior single-app `backend/` monolith was fully retired in **Phase 0** (2026-07-06) once the microservices split covered (and exceeded) its functionality.

---

## 2. Architecture

```
frontend (React/Vite) ──▶ api-gateway ──▶ identity-service
                                       ├─▶ package-service
                                       ├─▶ booking-service ──▶ (Feign) package-service
                                       ├─▶ media-service
                                       ├─▶ notification-service
                                       ├─▶ trip-service ──▶ (Feign) booking-service
                                       └─▶ payment-service
                                              │
                            all services ──▶ Firestore (travel2go-db)
                            trip-service ──▶ RabbitMQ (trip.exchange)
                            reactive-booking-function ◀── RabbitMQ (booking events)
```

`reactive-booking-function` is a standalone reactive consumer, not routed through the gateway — it reacts to booking events published to RabbitMQ.

---

## 3. Tech stack (full, all layers)

**Backend (every `microservices/*` Spring Boot module, uniform by design):**
- Java 17, Spring Boot 3.2.4 (parent POM pinned — deliberately *not* the `4.0.6` the retired `backend/` monolith used).
- Spring Cloud 2023.0.1; `spring-cloud-gcp-dependencies` 5.1.0 (Firestore integration).
- Spring Web (REST controllers), Spring Security (JWT filter chain, per-service copy — no shared security module across services by design).
- Spring Data Firestore (`FirestoreReactiveRepository`) — reactive repos even in otherwise-blocking services (`.block()` used at the service layer).
- Spring AMQP (RabbitMQ) — `trip-service` declares `trip.exchange` (topic exchange); `reactive-booking-function` consumes booking events reactively.
- OpenFeign (`spring-cloud-starter-openfeign`) — inter-service calls: `booking-service → package-service`, `trip-service → booking-service`; `FeignConfig` propagates the `Authorization` header on every Feign call.
- JJWT 0.11.5 (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) — JWT issuance/parsing (`JwtUtil`), and `QuoteTokenService`'s signed quote tokens.
- Lombok 1.18.38 (`@Data`, `@Builder`, `@NoArgsConstructor`/`@AllArgsConstructor`, `@RequiredArgsConstructor`).
- Spring Boot Actuator, Spring Boot Mail Starter, Spring Boot OAuth2 Client Starter (identity-service / api-gateway login).
- Google Cloud Storage client (media-service uploads), Google Cloud Firestore client.
- Maven (per-service `mvnw`/`mvnw.cmd`, wrapper files copied verbatim across services — never regenerated independently).
- Test stack: JUnit 5, Mockito, AssertJ. No MockMvc/`@WebMvcTest` anywhere in the repo — only two test patterns used: (1) plain Mockito unit test of a service class (e.g. `BookingEventProcessorTest`, `TripServiceTest`, `QuoteTokenServiceTest`, `PaymentServiceTest`), and (2) `@SpringBootTest` context-load test with `@MockBean` repositories and GCP autoconfig disabled (e.g. `BackendApplicationTests`, `TripServiceApplicationTests`, `PaymentServiceApplicationTests`).

**Frontend (`frontend/`):**
- React + Vite (JavaScript, not TypeScript).
- Build: Vite, `@vitejs/plugin-react` (Oxc-based) — the alternative SWC plugin noted in the template README but not adopted.
- ESLint (`eslint.config.js`) for linting; no TypeScript/`typescript-eslint` adopted.
- Routing/pages via plain React components under `src/pages`.
- Key runtime deps: `axios` (HTTP client, base URL defaults to gateway `localhost:8080/api`), `dompurify` (sanitizing rendered HTML), `lucide-react` (icons), `react-hot-toast` (notifications), `react-quill-new` (rich text editor for package descriptions).
- Firebase Hosting configured (`.firebaserc`, `firebase.json`) for the built frontend.
- No state-management library beyond React itself observed at this scan depth (services under `src/services`, utils under `src/utils`).

**Data / messaging / infra:**
- Firestore (`travel2go-db`), GCP project `travel2go-495007`, region `asia-south2` — primary datastore, no fixed schema, no formal migration tooling (see §6).
- RabbitMQ — `trip.exchange` topic exchange; local dev via `microservices/rabbitmq` compose config.
- Redis — local dev via `microservices/redis` compose config; production host injected via `SPRING_REDIS_HOST` secret (managed instance, provider undeclared in-repo — likely Memorystore).
- Google Cloud Storage — media/file uploads (media-service).
- Docker + Docker Compose — `docker-compose-microservices.yml` (current stack) and root `docker-compose.yml` (defines only `mongodb` — legacy/unclear relevance, flagged in §12).
- Google Cloud Run — deployment target for every backend service, `--no-allow-unauthenticated` + `roles/run.invoker` restriction on `trip-service`/`payment-service`/`booking-service`.
- GitHub Actions (`.github/workflows/backend-deploy.yml`) — CI/CD, per-service `CR_ENV_VARS` build step for scoped secrets.

### Services (`microservices/*`)

| Service | Entry point | Controllers | Role |
|---|---|---|---|
| **api-gateway** | `ApiGatewayApplication` | — | Spring Cloud Gateway; routes `/api/**`; OAuth2 login, JWT filter, `SecurityConfig`, rate limiting (`RateLimitingFilter`, `RequestCounter`) |
| **identity-service** | `BackendApplication` | `AuthController`, `SettingsController`, `UserController` | Login/register/OTP/forgot-password, JWT issuance (`JwtUtil`), global settings, user role management |
| **package-service** | `BackendApplication` | `HolidayPackageController`, `CustomPackageController`, `ConfiguratorCategoryController`, `AllocationRuleController`, `TravelConfigurationController` | Holiday/custom package CRUD, admin configurator categories, hotel/transport allocation rules, travel config |
| **booking-service** | `BackendApplication` | `BookingController`, `LeadController`, `LegBookingController` | Legacy package bookings (`POST /api/bookings`, **never modified — live**), leg bookings (`POST /api/leg-bookings`, Phase 0), lead capture/CRM |
| **trip-service** | `TripServiceApplication` | `TripController` | Owns `Trip`/`Leg`; per-resource ownership (`ownerUserId`) enforced; issues priced quote + `quoteToken` on leg add; validates its own leg's stored price/token before booking |
| **payment-service** | `PaymentServiceApplication` | `PaymentController` | `POST /api/payments` charge; `QuoteTokenService` validates HMAC tokens; `SandboxUpiProvider` stub (always `SUCCESS`) |
| **media-service** | `BackendApplication` | `MediaController` | Upload → Google Cloud Storage (`GcsStorageService`), returns `MediaFileDTO` |
| **notification-service** | `BackendApplication` | `NotificationController` | Email/SMS dispatch, consumed via `NotificationClient` |
| **reactive-booking-function** | `ReactiveBookingApplication` | `ReactiveBookingController` | Reactive RabbitMQ consumer of booking events (`AcknowledgableDelivery`, `BookingEventProcessor`) |
| **common-models** | — | — | Shared DTOs/entities, Maven dependency of every service |
| **redis**, **rabbitmq** | — | — | Infra-only config dirs, not app code |

### Shared models (`common-models`)

Entities: `Booking`, `Trip`, `Leg`, `Payment`, `User`, `HolidayPackage`, `CustomPackage`, `AllocationRule`, `ConfiguratorCategory`, `GlobalSettings`, `TravelConfiguration`, `Lead`, `LeadActivity`, `LeadAuditLog`, `Traveller`, `TravellerGroup`, `VerificationCode`.
DTOs: `BookingRequest`, `LegBookingRequest`, `LegBookingResponse`, `MediaFileDTO`.

### Firestore collections

`bookings` (legacy, extended with nullable `tripId`/`legId`/`quoteToken`/`amountPaise`/`feePaise`), `trips`, `legs`, `payments`, `travellers`, `traveller_groups` (reserved for a later phase). No formal migration tooling — `MIGRATIONS.md` is the manual audit trail (see §6).

---

## 4. Trip/leg booking flow (Phase 0 feature — the newest, most-hardened path)

1. Client adds a leg to a trip → `trip-service` computes price, signs a `quoteToken` (HMAC via `QuoteTokenService`), stores both on the `Leg`. Publishes `trip.created` / `leg.booked` events to `trip.exchange` (RabbitMQ).
2. Client calls `POST /api/trips/{id}/legs/{legId}/book` — **no request body**. Price/token can no longer be client-supplied (was a fixed vulnerability, see §7).
3. `trip-service.bookLeg` reads the leg's own stored price/token, validates via `QuoteTokenService`, then calls `booking-service`'s `POST /api/leg-bookings` (Feign, `Authorization` header propagated by `FeignConfig`).
4. `booking-service` persists the leg booking. It still has **no independent validation** of its own — trusts its caller entirely (open item, §7).

`QUOTE_TOKEN_SECRET` must be identical across `trip-service` and `payment-service` — `QuoteTokenService` is duplicated into both (same pattern as duplicated `JwtUtil`; this repo deliberately shares no security classes across services, per the Phase 0 plan's global constraints).

**Money invariant (G1):** all money fields are `Long` paise; `feePaise` on any payment/booking record must always be `0L` (zero booking/convenience fee) — enforced by tests (`QuoteTokenServiceTest`, `PaymentServiceTest`).

---

## 5. Frontend (`frontend/`)

React + Vite (template-generated scaffold per `frontend/README.md` — Vite HMR, `@vitejs/plugin-react` via Oxc, ESLint; React Compiler not enabled; no TypeScript).

**Pages:** `Home`, `Login`, `Register`, `ForgotPassword`, `OAuth2RedirectHandler`, `PackageDetails`, `ImageGallery`, `Admin`, `TrustPortal` (waitlist predictor, refund SLA dashboard, zero-fee calculator — most recent addition, commit `a793bd4`).
**Admin components:** `AdminAllocationRules`, `AdminConfigurator`, `AdminConfiguratorCategories`, `AdminLeads`, `AdminUsers`.
**Shared:** `MainLayout`, `Navbar`, `SEO`, `SkeletonLoader`.
**Key deps:** axios, dompurify, lucide-react, react-hot-toast, react-quill-new (rich text package descriptions).
**Firebase:** `.firebaserc` + `firebase.json` present — hosting for the built frontend.
**API base URL:** defaults to `localhost:8080` in `frontend/src/services/api.js` (comment: "Ensure it ends with /api for the gateway") — frontend always talks to `api-gateway`, never a service directly.

---

## 6. `MIGRATIONS.md` — full content (schema audit trail)

> Firestore has no migration tooling; this file is the manual log.

**2026-07-06 — Phase 0: Foundations**

New Firestore collections (all in `travel2go-db`, created on first write, no manual provisioning):
- `trips` — `Trip`, owned by `trip-service`.
- `legs` — `Leg`, owned by `trip-service`.
- `travellers` — `Traveller`, owned by `identity-service` in a later phase; collection reserved now.
- `traveller_groups` — `TravellerGroup`, same as above.
- `payments` — `Payment`, owned by `payment-service`.

Modified collection: `bookings` (owned by `booking-service`) — added five nullable fields to `Booking`: `tripId`, `legId`, `quoteToken`, `amountPaise`, `feePaise`. Existing documents unaffected; legacy `POST /api/bookings` never sets them, so they stay absent on old/legacy-flow documents. Only `POST /api/leg-bookings` populates them.

Deleted: the `backend/` monolith (entire Spring Boot app) and six duplicated `tree.txt` Maven dependency dumps under `microservices/*/`.

---

## 7. `OPEN_QUESTIONS.md` — full content (unresolved decisions / security state)

> External contracts and decisions stubbed behind an interface rather than guessed. Resolve before the corresponding phase ships to production.

### Deployment readiness (GitHub secrets + GCP IAM) — action required before next deploy

`backend-deploy.yml` references two secrets that must exist before the next push to `main`:
- **`TRIP_SERVICE_URL`** — trip-service's Cloud Run URL. Without it, `api-gateway` falls back to `http://trip-tbd` and cannot route any `/api/trips/**` request. Was missing entirely until fixed — meaning trip-service had never actually been reachable through the deployed gateway.
- **`QUOTE_TOKEN_SECRET`** — a real 32+ byte random secret, must be **identical** for `trip-service` and `payment-service` (same HMAC-signed tokens). A mismatch silently breaks every `bookLeg` call; omitting it falls back to the hardcoded-in-source placeholder (public in this repo), defeating the point of signing.

`trip-service`, `payment-service`, `booking-service` now deploy with `--no-allow-unauthenticated` + a `roles/run.invoker` grant to the project's default compute service account — closes off the public internet (removing `api-gateway` routes alone did **not**, since Cloud Run services are independently publicly addressable by default). **Partial mitigation, not true per-service isolation**: every Cloud Run service in this project runs as the same default compute service account, so the grant really means "any Cloud Run workload in this project can call these three services," not "only api-gateway/trip-service can." Real isolation needs dedicated service accounts per service + `--service-account` on deploy — a bigger change not done here (needs confirming the deploying `GCP_CREDENTIALS` principal can create service accounts, and that moving off the default SA doesn't strip IAM roles currently relied on for Firestore/GCS/Pub-Sub). Also needs confirming in the GCP console that every service actually runs as the default compute SA as assumed.

`QUOTE_TOKEN_SECRET`/`TRIP_SERVICE_URL` are scoped in the workflow to only the services that read them (per-service `CR_ENV_VARS` build step) — unlike the pre-existing secrets (`JWT_SECRET`, `TWILIO_*`, etc.) which are still sprayed to all nine services in the deploy matrix (that sprawl predates this work, wasn't touched; new secrets shouldn't add to it).

### Payments (Phase 0, `payment-service`)
- **UPI provider**: Razorpay vs. Cashfree not chosen; no API keys in repo. `PaymentProvider` implemented only by `SandboxUpiProvider` (always `SUCCESS`, synthetic `providerRef`). Swap in real implementation behind the same interface once provider + keys confirmed.
- **Quote-token secret**: `quote.token.secret` ships a placeholder default in `application.properties`, duplicated identically in `payment-service` (issues + validates) and `trip-service` (validates only). See `QUOTE_TOKEN_SECRET` above.

### Infrastructure (Phase 0 architecture report, carried forward)
- **Redis/RabbitMQ in production**: `.github/workflows/backend-deploy.yml` injects `SPRING_REDIS_HOST`/`SPRING_RABBITMQ_HOST` from GitHub secrets rather than docker-compose hostnames, implying managed instances (Memorystore, CloudAMQP) — exact provider not declared anywhere in repo.
- **`backend/` monolith retirement**: confirmed unreferenced by CI, docker-compose, frontend; deleted in Phase 0 Task 1. If anyone was still hitting it directly (bypassing the gateway), that's now broken — watch for reports.

### Security follow-ups (Phase 0, found during build)

- **`TripService.bookLeg` used to trust client-supplied `amountPaise`/`quoteToken` — fixed.** Now ignores request-body price/token entirely; reads the `Leg`'s own stored `pricePaise`/`quoteToken`, validates via `QuoteTokenService` before ever calling `booking-service`. Client can no longer alter charged amount by tampering with the booking request; invalid stored token (expired/tampered/missing) throws before `booking-service` is called. `POST /api/trips/{id}/legs/{legId}/book` no longer takes a request body — `BookLegRequest` deleted as unused. Closes the "two-layered" gap on the `trip-service` side.
- **`booking-service`'s `POST /api/leg-bookings` still has no quote-token validation or trip-ownership check of its own** — blindly persists whatever `LegBookingRequest` it receives. Risk is smaller in practice now (its only real caller, `trip-service.bookLeg`, always sends an already-validated price/token pair) and the endpoint is no longer directly callable from outside the service mesh (`--no-allow-unauthenticated` + IAM invoker restriction) — but still has no defense of its own if reached by another service later. Still open: whether a leg booking should only be marked `CONFIRMED` after `payment-service.charge(...)` succeeds, rather than immediately on request — untouched by this fix.
- **`payment-service`'s `POST /api/payments` (`PaymentController.charge`) had no ownership/ACL check on caller-supplied `bookingRef` — fixed by removing it from the public gateway and restricting its Cloud Run invoker.** Gateway route `[9]` (`Path=/api/payments/**`) deleted; service deploys `--no-allow-unauthenticated` — no longer reachable from outside the mesh at either layer. `payment-service` has no `Trip`/`Booking` data of its own, so can't resolve `bookingRef → ownerUserId` directly — adding that needs a new cross-service dependency (payment-service calling back into trip-service), not justified while nothing calls `charge()` in a real flow yet. Re-evaluate when `trip-service` (or a future billing/refund flow) wires up to call `payment-service.charge(...)` internally.
- **`trip-service` initially shipped (Task 3) with no per-resource authorization** — any authenticated user could read/mutate any other user's `Trip` by guessing/enumerating a `tripId`. Found by automated security review, fixed within Phase 0 (commit `67bb60f`): `TripController`/`TripService` now bind `ownerUserId` on trip creation, reject non-owner reads/writes with `AccessDeniedException`. No action needed — build-history record; landed as an unplanned addition to the Phase 0 plan.

### Phase 1+ (from the PRD, not yet started)
- IRCTC authorised-partner access for `inventory-service`'s `RailAdapter` — available, or rail launches management-only over public data first?
- WhatsApp Business API BSP (Gupshup / Meta direct / other) + template approval status, for `notification-service`'s WhatsApp channel.
- Firestore's fit for itinerary/monitoring queries at scale (e.g. "all trips with a leg delayed in the next 6h") — needs validation before `journey-monitor-function` (Phase 2) scales.

---

## 8. Phase 0 implementation plan — full breakdown

Source: `docs/superpowers/plans/2026-07-06-phase0-foundations.md` (a `superpowers`-formatted agentic implementation plan; drove the current trip/payment architecture). Summarized here — full plan retains the literal code scaffolding if needed for reference.

**Goal:** retire the dead `backend/` monolith; add `Trip`/`Leg`/`Traveller`/`TravellerGroup` to `common-models`; stand up `trip-service` and `payment-service` as new Cloud Run microservices; wire `booking-service` to accept leg-level bookings orchestrated by `trip-service`; clean up the repo (`tree.txt` dumps, add `OPEN_QUESTIONS.md`/`MIGRATIONS.md`).

**Architecture decision:** two new Spring Boot 3.2.4 services join the fleet behind `api-gateway`, following the exact pom/Dockerfile/Firestore/JWT pattern already used by `booking-service`/`media-service`. `trip-service` becomes the new spine — owns `Trip`/`Leg` Firestore collections, publishes to `trip.exchange` (RabbitMQ topic exchange), calls `booking-service` via Feign (same pattern `booking-service` already used to call `package-service`). `payment-service` introduces `quoteToken` (signed JWT carrying legId + price) that must validate before any charge — code-level enforcement of "first price = final price."

**Tech stack:** Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.1, spring-cloud-gcp 5.1.0 (Firestore), Spring AMQP (RabbitMQ), OpenFeign, JJWT 0.11.5, Lombok 1.18.38, JUnit 5 + Mockito + AssertJ, Maven per-service `mvnw`.

**Global constraints (repo-wide conventions):**
- Parent POM `spring-boot-starter-parent:3.2.4` for every microservice (not the `4.0.6` the retired `backend/` monolith used).
- `spring-cloud.version=2023.0.1`, `spring-cloud-gcp-dependencies:5.1.0`, Java 17, Lombok 1.18.38.
- groupId `com.travel2go`, base package `com.travel2go.backend` for every service.
- GCP project `travel2go-495007`, Firestore database `travel2go-db`, region `asia-south2` — literals copied everywhere, never re-derived.
- Money fields are `Long` paise; `feePaise` always `0L` (G1) — every charge-path change needs a test asserting this.
- `payment-service` rejects a charge whenever `quoteToken` doesn't match the leg+price it was issued for (G2), enforced by `QuoteTokenService`.
- Maven wrapper copied verbatim from `booking-service/` into every new service — never regenerated.
- Every service's Dockerfile identical: `FROM eclipse-temurin:17-jdk-jammy`, copies `target/*.jar`, `EXPOSE ${PORT}`, `ENTRYPOINT ["java","-jar","app.jar"]`.
- `server.port=${PORT:8080}`; actual port supplied via `SERVER_PORT` (compose) or Cloud Run's `PORT=8080` convention — never hardcoded elsewhere.
- Every new controller sits behind the per-service copied `JwtAuthenticationFilter`+`SecurityConfig` — **no shared security classes across services**, by design (not an oversight).
- Legacy `POST /api/bookings` never modified — only additive endpoints alongside it.
- Test style: no MockMvc/`@WebMvcTest` in this repo. Two patterns only: (1) Mockito unit test of the service class (see `BookingEventProcessorTest`), (2) `@SpringBootTest` "contextLoads" with `@MockBean` repos and GCP autoconfig disabled (see `BackendApplicationTests`).

**Task list:**

1. **Audit and retire the `backend/` monolith** — confirm no CI/compose/frontend reference it; confirm every `backend/` controller has a `microservices/` counterpart (microservices split found strictly ahead: `booking-service` has `LeadController` which the monolith never had); explicit user confirmation required before `git rm -r backend/` (large, hard-to-reverse deletion — plan itself calls this out as a stop-and-confirm gate); verify no other pom depends on an artifact literally named `backend`.
2. **Add `Trip`, `Leg`, `Traveller`, `TravellerGroup` to `common-models`** — TDD: write `NewModelsTest.java` (builder round-trip assertions) first, confirm it fails to compile, then add the four `@Document`-annotated Firestore model classes (`trips`/`legs`/`travellers`/`traveller_groups` collections), confirm tests pass, `mvn install` so downstream services can consume the jar.
   - `Trip`: `id, ownerUserId, title, status (DRAFT|BOOKED|IN_PROGRESS|COMPLETED|CANCELLED), travellerIds, legIds, origin, destination, startDate, endDate, budgetPaise, savingsLedgerPaise, createdAt, updatedAt`.
   - `Leg`: `id, tripId, type (RAIL|BUS|HOTEL|FLIGHT|PACKAGE), status (SEARCHING|SELECTED|WAITLISTED|CONFIRMED|CANCELLED|COMPLETED|DISRUPTED), supplierRef, pnr, confirmationProbability, pricePaise, quoteToken, startAt, endAt, metadata`.
   - `Traveller`: `id, ownerUserId, fullName, dateOfBirth, idType (AADHAAR|PASSPORT|DL|OTHER), idReferenceToken (tokenised — never raw ID, DPDP compliance), gender, relationship (SELF|SPOUSE|CHILD|PARENT|OTHER), isMinor`.
   - `TravellerGroup`: `id, ownerUserId, name, travellerIds`.
3. **Scaffold `trip-service`** — full Maven module (pom, Dockerfile, mvnw), `TripServiceApplication` (`@EnableFeignClients`), `FirestoreConfig`, `RabbitMQConfig` (declares `trip.exchange` topic exchange), `FeignConfig` (propagates `Authorization` header to Feign calls), copied JWT security stack, `TripRepository`/`LegRepository` (Firestore reactive repos), `TripEventPublisher`, `TripService` (`createTrip`, `getTripDetail`, `addLeg`; `bookLeg` added in Task 5), `TripController` (`POST /api/trips`, `GET /api/trips/{id}`, `POST /api/trips/{id}/legs`, `POST /api/trips/{id}/legs/{legId}/book`), DTOs (`CreateTripRequest`, `AddLegRequest`, `TripDetailResponse` — `BookLegRequest` was later deleted, see §6). Built TDD-first against `TripServiceTest` (Mockito unit tests). Wired into `docker-compose-microservices.yml`, `api-gateway` route `[8]`, and the deploy workflow matrix.
4. **Scaffold `payment-service`** — same scaffold pattern. Adds `Payment` model to `common-models` (`id, bookingRef, method (UPI|CARD|NETBANKING), status (SUCCESS|FAILED), amountPaise, feePaise (must always be 0 — G1), providerRef, quoteTokenValidated, createdAt`). `QuoteTokenService.issue(legId, pricePaise)`/`isValid(token, legId, pricePaise)`; `PaymentService.charge(bookingRef, amountPaise, method, quoteToken)`; `PaymentProvider` interface + `SandboxUpiProvider` stub (`PaymentResult` always `SUCCESS`). `PaymentController` → `POST /api/payments`. TDD via `QuoteTokenServiceTest`/`PaymentServiceTest`.
5. **Repurpose `booking-service` for leg-level bookings** — new `LegBookingController` (`POST /api/leg-bookings`), orchestrated by `trip-service.bookLeg` calling in via the now-fleshed-out `BookingClient` Feign client. Legacy `POST /api/bookings` untouched.
6. **Cleanup** — delete six stale `tree.txt` Maven dependency-tree dumps (all identical copy-paste artifacts for `com.travel2go:backend`, consumed by nothing); author `OPEN_QUESTIONS.md` and `MIGRATIONS.md` at repo root (content now superseded/carried forward into §6–7 of this doc, since both files evolved after this plan was written — e.g. `QUOTE_TOKEN_SECRET`/`TRIP_SERVICE_URL` deployment-readiness section and the three security-fix write-ups aren't in the plan's original draft text).

**Phase 0 exit checklist** (from the plan):
- [x] Only `microservices/` builds/deploys; `backend/` gone.
- [x] `Trip`/`Leg`/`Traveller`/`TravellerGroup` compile, shared via `common-models`.
- [x] A trip can be created, a leg added, full itinerary retrieved; `trip.created` publishes to `trip.exchange`.
- [x] A payment succeeds only when `quoteToken` matches leg+price; `feePaise` always `0`.
- [x] Booking a leg updates the Trip's leg, emits `leg.booked`.
- [x] Repo tidy; unknowns logged in `OPEN_QUESTIONS.md`.

(All items appear satisfied per current source tree and `OPEN_QUESTIONS.md`'s later security-fix entries, which post-date this plan.)

---

## 9. `frontend/README.md` — full content

Standard Vite React template readme (not project-specific):
- Minimal Vite + React + HMR setup.
- Two official plugin options noted: `@vitejs/plugin-react` (uses Oxc) or `@vitejs/plugin-react-swc` (uses SWC).
- React Compiler not enabled (perf impact on dev/build) — link to enable it if desired.
- Note on expanding ESLint config with TypeScript + `typescript-eslint` for production apps (this project is plain JS, not adopted).

---

## 10. Hotel & Transport Allocation business spec (from `scratch/step_168.md`)

`scratch/step_168.md` is a raw captured prompt (an "Antigravity UI/UX" spec request, dated 2026-06-19, editor-context metadata attached), not an authored project doc — but it documents the intended business logic behind `AllocationRule`/`ConfiguratorCategoryController` in `package-service`, so it's summarized here for completeness. Truncated in the source (~2022 bytes cut mid-document).

**Age classification** (applied to every guest before allocation):
- Adult (A): age ≥ 12.
- Child With Bed (CWB): 5 ≤ age < 12 and bed required.
- Child No Bed (CNB): 5 ≤ age < 12 and no bed required.
- Infant (I): age < 5 (always no bed).

**Room allocation rules:**
- Max occupancy per standard room: 3 adults, or 2 adults + 2 children.
- Default base: 2 adults/room (double sharing).
- Sequential allocation by adult count (1 → single occupancy; 2 → double sharing, adjusted for 0/1/2 children present, with a 2-children case forced into 2 rooms or a family room "due to hotel safety laws"; 3 → triple sharing with extra mattress, or split into double+single as an alternative; >3 → `ceil(adults/2)` rooms, distributed as double sharing, remainder becomes triple or single).

**Pricing:** total package cost = hotel cost (all rooms × nights) + fixed transport cost + sightseeing tickets; per-adult price = total fixed cost ÷ total adults (child costs broken out separately).

**UI/UX requirements noted:** dynamic guest-addition form (name, age, bed preference for ages 5–11); manual override toggle to convert a triple-sharing room into 1 double + 1 single; vehicle-upgrade dropdown (e.g. Sedan → Innova Crysta) with real-time per-head cost recalculation.

(The remainder of the spec — vehicle capacity rules, exact per-night hotel cost formula — was truncated in the captured source file and isn't recoverable from it alone; check `AllocationRuleController`/`AllocationRule` model and the `AdminAllocationRules.jsx` frontend component for the as-built logic if this needs to be reconciled with spec.)

---

## 11. Infra / deployment

- `docker-compose-microservices.yml` — full local stack: api-gateway, identity/package/booking/media/notification/trip/payment-service, frontend, redis, rabbitmq, reactive-booking-function.
- `docker-compose.yml` — root-level, defines only `mongodb` per the Phase 0 plan's Task 1 audit; likely a pre-microservices leftover — worth confirming still needed.
- CI: `.github/workflows/backend-deploy.yml` deploys to Cloud Run; `trip-service`/`payment-service`/`booking-service` deploy `--no-allow-unauthenticated` + restricted `run.invoker`.
- Secrets required: `QUOTE_TOKEN_SECRET` (must match trip/payment-service), `TRIP_SERVICE_URL`, `JWT_SECRET`, `TWILIO_*`, GCP creds, etc.

---

## 12. Repo hygiene notes

- Root has stray test/debug artifacts not part of the build: `TestFormat.java/.class`, `TestFormat2.*`, `TestPdf.java/.class`, `TestRichText.java/.class`, `test.pdf`, `check_str.js`, `test_strip.js`.
- `scratch/` — ad hoc JS scripts (`find_navbar.js`, `find_pricing_card.js`, `find_rendering.js`, `find_specs.js`, `get_diff.js`, `save_step.js`, `search_pax_matrix.js`) plus a diff and the `step_168.md` prompt capture (§10) — exploratory, not shipped code.
- `graphify-out/` — knowledge-graph output from a prior `/graphify` run, dated 2026-07-06 — predates the `backend/` monolith deletion and the addition of `trip-service`/`payment-service`; treat as historical only, not current architecture.
- Root `package.json` is an empty `{}` — no root-level npm tooling actually configured.
- `docs/superpowers/plans/2026-07-06-phase0-foundations.md` is a large (2500+ line) agentic implementation plan with full inline code scaffolding for every Phase 0 file — kept here as build-history record; §8 above is its summary.

---

## 13. Suggested reading order for a newcomer

1. §6–7 above (`MIGRATIONS.md` + `OPEN_QUESTIONS.md` content) — most current source of "why," especially the security fix history.
2. `microservices/trip-service` + `microservices/payment-service` source — newest, most actively hardened flow (leg booking + quote tokens), cross-referenced against §3 (tech stack) and §8 (Phase 0 plan) above.
3. `microservices/api-gateway` — routing/security entry point.
4. `frontend/src/pages/TrustPortal.jsx` — most recent frontend feature.
5. §10 above + `AllocationRule`/`ConfiguratorCategoryController` — if working on package pricing/room allocation.
