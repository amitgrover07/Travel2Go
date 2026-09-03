# F1 + F2 — CI test gate + trip-service readiness fix (design)

Status: approved for planning
Scope: a new `.github/workflows/backend-ci.yml`; standardizing the context-load test pattern across all 9 Java microservices so `./mvnw clean verify` (no `-DskipTests`) actually passes for each; the trip-service `circuitBreakers` readiness fix. Does not touch `backend-deploy.yml`, does not build P1.3/P1.4.

## Source

`FIX-PLAN.md` (F1, F2, sequenced before P1.3). F2's brief is 2 sentences; F1's is a checklist. Investigation below fills in what's actually needed for both.

## Correction to FIX-PLAN.md's own diagnosis (verified empirically before writing this spec)

FIX-PLAN.md's F2 section claims trip-service's `circuitBreakers` boot failure needs a code/pom fix. **It does not.** Investigated and tested directly:

- `trip-service/pom.xml` declares `spring-cloud-starter-circuitbreaker-resilience4j` (Spring Cloud's `CircuitBreakerFactory` abstraction). `mvnw dependency:tree` confirms this transitively pulls in `io.github.resilience4j:resilience4j-spring-boot3:2.1.0` — the artifact that provides the actuator health indicator auto-configuration. It was never missing.
- (A P1.2 code reviewer earlier concluded this dependency was absent, based on checking only trip-service's *direct* pom entries — that conclusion was wrong, and this session repeated it without re-verifying. Corrected here.)
- The actual fix is exactly what FIX-PLAN.md's F2 literally proposes: add `management.health.circuitbreakers.enabled=true` to `trip-service/application.properties`, and restore `circuitBreakers` in `management.endpoint.health.group.readiness.include`. Tested directly: `TripServiceApplicationTests` passes, full suite exit 0. No pom.xml change of any kind.

## Current state (verified against the repo, 2026-09-03)

- Only workflow that runs Java service builds: `backend-deploy.yml`, `push`-triggered, `./mvnw clean package -DskipTests`. No PR-triggered test job exists at all — confirms FIX-PLAN.md's F1 premise exactly.
- **Two incompatible context-load test patterns exist side by side:**
  - `booking-service` / `trip-service` / `payment-service`: `spring.cloud.gcp.firestore.enabled=false` (+ `.storage.enabled=false`, `.core.enabled=false`) plus `@MockBean` on every `@Repository` the service declares. No real infrastructure needed. Already proven working (all three pass today).
  - `identity-service`: a **Firestore-emulator**-based test instead (`spring.cloud.gcp.firestore.emulator.enabled=true`, `host-port=localhost:8080`) — expects a real emulator process, which nothing in this repo starts.
  - `package-service` / `media-service` / `notification-service`: bare `@SpringBootTest`, no GCP-disable, no repository mocks.
  - `api-gateway`: **no context-load test file exists at all.**
  - `reactive-booking-function`: has its own existing test suite (`ReactiveBookingApplicationTest`, `BookingEventProcessorTest`) — a 9th Java service, not named in FIX-PLAN.md's "8 services" framing (user confirmed: include it).
- **Confirmed currently broken, right now, on `main`:** running `identity-service`'s existing context test directly (`JWT_SECRET` set, nothing else) fails with `IllegalArgumentException: Illegal character found in host: '{'` — a Feign client URL property (`NOTIFICATION_SERVICE_URL`) is unset, so its unresolved `${NOTIFICATION_SERVICE_URL}` placeholder text gets fed straight into `java.net.URL`. This is exactly the class of bug F1 exists to catch, already present, undetected, because nothing runs this test today.
- `package-service`/`media-service`/`notification-service`'s bare `@SpringBootTest` tests have not been individually run yet to confirm their exact failure mode — expected to fail against real Firestore/GCP auto-config the same general way, exact error TBD per-service until run (each gets diagnosed and fixed at implementation time, same way `identity-service`'s Feign-URL issue was just found by running it).
- Every service's *required* (no-default) `${VAR}` placeholders, enumerated directly from each `application.properties`:
  - `identity-service`, `package-service`, `media-service`: `JWT_SECRET`
  - `booking-service`, `trip-service`: `JWT_SECRET`, `QUOTE_TOKEN_SECRET`
  - `payment-service`: `JWT_SECRET`, `QUOTE_TOKEN_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`
  - `notification-service`: none
  - `api-gateway`: `IDENTITY_SERVICE_URL`, `PACKAGE_SERVICE_URL`, `BOOKING_SERVICE_URL`, `MEDIA_SERVICE_URL`, `TRIP_SERVICE_URL`, `PAYMENT_SERVICE_URL`
  - `reactive-booking-function`: not yet enumerated (existing tests may already handle this — check at implementation time).
- `A2`'s fail-fast pattern means any of the `*_SECRET` values above must be ≥32 bytes in CI or the service's own `@PostConstruct` check rejects it before the context even gets to the point F1 is trying to test.

## Decisions

1. **Standardize on the disable-GCP + mock-repos pattern**, not the Firestore-emulator pattern, for every service. Simpler (no emulator container to run in CI), and 3 services already prove it works. `identity-service`'s test is rewritten to match; `package-service`/`media-service`/`notification-service` get the pattern added fresh (mocking whichever `@Repository` interfaces each actually declares — enumerated per-service at implementation time, since Spring Data creates a proxy bean for every detected repository interface regardless of whether anything injects it, so *all* of them need mocking or GCP-disabling to avoid a real Firestore attempt).
2. **`api-gateway` gets a new context-load test created** (`ApiGatewayApplicationTests` or similar) — it currently has none, and it's the service where a downstream-URL fail-fast bug (`DownstreamUrlValidator`) would matter most to catch.
3. **`reactive-booking-function` joins the CI matrix** (user-approved scope addition) — its existing tests just need wiring in, not a new pattern.
4. **Dummy secrets/URLs live in the workflow's per-job `env:` block**, not a checked-in `application-test.properties` file — keeps the "what's fake" visible in one place (the workflow itself) rather than scattered across 9 modules, and matches the `KEY=value ./mvnw test` pattern already used throughout this session's local verification. Every `*_SECRET` dummy is ≥32 bytes; every `*_URL` dummy is a syntactically valid `http://` URL (e.g. `http://test-identity`).
5. **`backend-deploy.yml` is untouched.** Its `-DskipTests` stays — deploys don't re-run tests, the new PR-triggered workflow is the gate.
6. **F2 lands as a 2-line property change**, no dependency change, per the empirical correction above.
7. **Branch protection** (making the new workflow a required status check on `main`) is a GitHub repository setting, not a code change — out of scope for this implementation, flagged in hand-back for manual setup.

## Architecture

```
.github/workflows/backend-ci.yml
  on: pull_request, push (main), paths: microservices/**

  job: build-shared
    - checkout, JDK 17 temurin, maven cache
    - cd microservices/common-models && ./mvnw -q clean install
    - cd microservices/platform-security && ./mvnw -q clean install

  job: test (matrix: 9 services, needs build-shared)
    - checkout, JDK 17 temurin, maven cache
    - re-install common-models + platform-security (each matrix job is a fresh runner)
    - cd microservices/${{ matrix.service }} && ./mvnw -q clean verify
      env: <per-service dummy secrets/URLs from the table above>
```

Each matrix job is an independent runner (GitHub Actions doesn't share filesystem state across matrix jobs), so `common-models`/`platform-security` must be rebuilt into that runner's local `.m2` in every job, not just once — mirrors what every task dispatch in this session's P1.1/P1.2 work already did locally.

## Data flow / what changes per service

| Service | Change |
|---|---|
| `trip-service` | F2: `management.health.circuitbreakers.enabled=true` + restore `circuitBreakers` in readiness group (2 lines, `application.properties`) |
| `identity-service` | Rewrite `BackendApplicationTests` to the disable-GCP + mock-repos pattern; diagnose and fix the `NOTIFICATION_SERVICE_URL` Feign issue (likely just needs the dummy env var supplied in CI — confirm no code fix is actually needed once the var is set) |
| `package-service`, `media-service`, `notification-service` | Add disable-GCP + mock-repos to each `BackendApplicationTests`; fix whatever surfaces once each runs for the first time |
| `api-gateway` | New context-load test file, disable-GCP pattern (api-gateway has no Firestore dependency itself, but should still get a standard `@SpringBootTest` with the 6 dummy `*_SERVICE_URL` vars to exercise `DownstreamUrlValidator`) |
| `booking-service`, `trip-service`, `payment-service` | No test-pattern change needed (already correct) — just added to the new CI matrix |
| `reactive-booking-function` | Added to the CI matrix; existing tests checked for their own required env vars at implementation time |
| `.github/workflows/backend-ci.yml` | New file |

## Error handling / testing

The "test" here is the workflow itself: a PR that breaks any service's compile, any existing behavioral test (`LegBookingServiceTest`, `PaymentServiceTest`, etc. — all already exist and already pass locally, this just makes CI run them), or any service's context-load now fails the required check and can't merge. No new application-level tests beyond the context-load tests being added/fixed per the table above.

## Acceptance criteria

1. `.github/workflows/backend-ci.yml` exists, triggers on `pull_request` and `push` to `main`, path-filtered to `microservices/**`.
2. All 9 Java services run `./mvnw clean verify` (no `-DskipTests`) in the matrix and pass.
3. `trip-service` boots with `circuitBreakers` back in its readiness group; `TripServiceApplicationTests` proves it (already does, once the 2-line fix lands).
4. `backend-deploy.yml` is unmodified.
5. No in-source secret defaults are added anywhere to make CI easier (A2 pattern holds) — all dummy values live in the workflow file only.

## Out of scope (explicitly deferred)

- Branch-protection / required-status-check GitHub setting (manual, flagged in hand-back).
- P1.3 (Postgres ledger), P1.4 (saga/outbox), P1.5 (leg-booking notifications) — FIX-PLAN.md's own stated sequencing places all three after F1/F2.
- Enabling `management.health.circuitbreakers.enabled` on `booking-service`/`identity-service` — FIX-PLAN.md explicitly says not to (their breakers include `notification`, and the composite indicator would wrongly couple readiness to a notification outage — exactly what B6 was designed to avoid). They keep `readiness.include=readinessState` only.
