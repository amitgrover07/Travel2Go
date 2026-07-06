# Travel2Go Phase 0 — Foundations & Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay the foundation for the Travel2Go journey platform: retire the dead `backend/` monolith, add the `Trip`/`Leg`/`Traveller`/`TravellerGroup` domain model to `common-models`, stand up `trip-service` and `payment-service` as new Cloud Run microservices, wire `booking-service` to accept leg-level bookings orchestrated by `trip-service`, and clean up the repo (`tree.txt` dumps, `OPEN_QUESTIONS.md`, `MIGRATIONS.md`).

**Architecture:** Two new Spring Boot 3.2.4 microservices (`trip-service`, `payment-service`) join the existing `microservices/` fleet behind `api-gateway`, following the exact pom/Dockerfile/Firestore/JWT pattern already used by `booking-service` and `media-service`. `trip-service` becomes the new spine: it owns `Trip`/`Leg` Firestore collections, publishes to a new `trip.exchange` RabbitMQ topic exchange, and calls `booking-service` via Feign (the same client pattern `booking-service` already uses to call `package-service`). `payment-service` introduces a `quoteToken` (a signed JWT carrying legId + price) that must validate before any charge — this is the code-level enforcement of "first price = final price."

**Tech Stack:** Java 17, Spring Boot 3.2.4, Spring Cloud 2023.0.1, spring-cloud-gcp 5.1.0 (Firestore), Spring AMQP (RabbitMQ), OpenFeign, JJWT 0.11.5, Lombok 1.18.38, JUnit 5 + Mockito + AssertJ, Maven (per-service `mvnw`).

## Global Constraints

- Parent POM: `org.springframework.boot:spring-boot-starter-parent:3.2.4` (matches `booking-service`/`media-service`/`reactive-booking-function` — **not** the `4.0.6` used by the `backend/` monolith being retired).
- `spring-cloud.version=2023.0.1`, `spring-cloud-gcp-dependencies` version `5.1.0`, `java.version=17`, `lombok.version=1.18.38`.
- `groupId` for every new module: `com.travel2go`. Base package for every class: `com.travel2go.backend` (matches every existing microservice).
- GCP project `travel2go-495007`, Firestore database `travel2go-db`, region `asia-south2` (do not introduce new values — copy these literals).
- Money fields are `Long` paise. `feePaise` on any payment/booking record must always be `0L` — every task touching a charge path needs a test asserting this (G1).
- `payment-service` must reject a charge whenever the supplied `quoteToken` doesn't match the leg + price it was issued for (G2) — enforced by `QuoteTokenService`, tested directly.
- Maven wrapper: copy `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` verbatim from `microservices/booking-service/` into every new service directory — do not regenerate them.
- Dockerfile for every service is identical: `FROM eclipse-temurin:17-jdk-jammy`, copies `target/*.jar`, `EXPOSE ${PORT}`, `ENTRYPOINT ["java", "-jar", "app.jar"]`.
- `server.port=${PORT:8080}` in every service's `application.properties`; the actual port is supplied by `SERVER_PORT` env var in `docker-compose-microservices.yml` (Spring's relaxed env-var binding overrides the property file) and by Cloud Run's own `PORT=8080` convention in production — do not hardcode a different port anywhere else.
- Every new REST controller sits behind the existing JWT filter chain (`JwtAuthenticationFilter` + `SecurityConfig`, copied per-service — this repo does not share security classes across services, so don't try to extract them into `common-models` in this plan).
- Never modify the legacy `POST /api/bookings` endpoint or its request/response shape in `booking-service` — it is live. Only add new endpoints/fields alongside it.
- Test style: this repo has **no MockMvc/`@WebMvcTest` precedent**. Follow the two patterns that exist today — (1) a `Mockito` unit test of the service/business-logic class (see `BookingEventProcessorTest`), and (2) a `@SpringBootTest` "contextLoads" test with `@MockBean`-ed repositories and Firestore/GCP autoconfig disabled (see `BackendApplicationTests`). Do not introduce MockMvc.

---

## File structure

**New module: `microservices/trip-service/`**
- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `Dockerfile` — copied/adapted service scaffold.
- `src/main/java/com/travel2go/backend/TripServiceApplication.java` — Spring Boot entry point, `@EnableFeignClients`.
- `src/main/java/com/travel2go/backend/config/FirestoreConfig.java` — Firestore project/database wiring.
- `src/main/java/com/travel2go/backend/config/RabbitMQConfig.java` — declares the `trip.exchange` topic exchange.
- `src/main/java/com/travel2go/backend/config/FeignConfig.java` — propagates the `Authorization` header to Feign calls.
- `src/main/java/com/travel2go/backend/security/{JwtUtil,JwtAuthenticationFilter,SecurityConfig}.java` — JWT auth, copied pattern.
- `src/main/java/com/travel2go/backend/model` — none; `Trip`/`Leg` live in `common-models` so `booking-service` can share them.
- `src/main/java/com/travel2go/backend/repository/{TripRepository,LegRepository}.java` — Firestore reactive repos.
- `src/main/java/com/travel2go/backend/service/{TripEventPublisher,TripService}.java` — event publishing + trip/leg business logic.
- `src/main/java/com/travel2go/backend/client/BookingClient.java` — Feign client calling `booking-service`'s new leg-booking endpoint.
- `src/main/java/com/travel2go/backend/controller/TripController.java` — `POST /api/trips`, `GET /api/trips/{id}`, `POST /api/trips/{id}/legs`, `POST /api/trips/{id}/legs/{legId}/book`.
- `src/main/java/com/travel2go/backend/dto/{CreateTripRequest,AddLegRequest,BookLegRequest,TripDetailResponse}.java`.
- `src/main/resources/application.properties`.
- `src/test/java/com/travel2go/backend/{TripServiceApplicationTests,service/TripServiceTest}.java`.

**New module: `microservices/payment-service/`**
- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `Dockerfile`.
- `src/main/java/com/travel2go/backend/PaymentServiceApplication.java`.
- `src/main/java/com/travel2go/backend/config/FirestoreConfig.java`.
- `src/main/java/com/travel2go/backend/security/{JwtUtil,JwtAuthenticationFilter,SecurityConfig}.java`.
- `src/main/java/com/travel2go/backend/service/{QuoteTokenService,PaymentService}.java`.
- `src/main/java/com/travel2go/backend/provider/{PaymentProvider,PaymentResult,SandboxUpiProvider}.java`.
- `src/main/java/com/travel2go/backend/repository/PaymentRepository.java`.
- `src/main/java/com/travel2go/backend/controller/PaymentController.java` — `POST /api/payments`.
- `src/main/java/com/travel2go/backend/dto/ChargeRequest.java`.
- `src/main/resources/application.properties`.
- `src/test/java/com/travel2go/backend/{PaymentServiceApplicationTests,service/{QuoteTokenServiceTest,PaymentServiceTest}}.java`.

**Modified: `microservices/common-models/`**
- `src/main/java/com/travel2go/backend/model/{Trip,Leg,Traveller,TravellerGroup,Payment}.java` — new.
- `src/main/java/com/travel2go/backend/model/Booking.java` — add `tripId`, `legId`, `quoteToken`, `amountPaise`, `feePaise` (all nullable, backward compatible).
- `src/main/java/com/travel2go/backend/dto/{LegBookingRequest,LegBookingResponse}.java` — new, shared between `booking-service` and `trip-service`.
- `src/test/java/com/travel2go/backend/model/NewModelsTest.java` — new, builder round-trip tests.

**Modified: `microservices/booking-service/`**
- `src/main/java/com/travel2go/backend/controller/LegBookingController.java` — new, `POST /api/leg-bookings`.
- `pom.xml` — no change (already has Firestore, common-models, web, security).

**Modified: infra/deploy**
- `docker-compose-microservices.yml` — add `trip-service`, `payment-service` blocks; extend `api-gateway`'s `environment`/`depends_on`.
- `microservices/api-gateway/src/main/resources/application.properties` — add gateway routes `[8]` (trip-service) and `[9]` (payment-service).
- `.github/workflows/backend-deploy.yml` — add `trip-service` and `payment-service` to the deploy matrix.

**New: repo root**
- `OPEN_QUESTIONS.md`, `MIGRATIONS.md`.

**Deleted**
- `backend/` (entire monolith, gated behind explicit confirmation — see Task 1).
- `backend/tree.txt` (goes with the directory), `microservices/*/tree.txt` (all 7 remaining, stale duplicate Maven dumps).

---

### Task 1: Audit and retire the `backend/` monolith

**Files:**
- Read-only audit, then delete: `backend/` (entire directory tree)

**Interfaces:** None — this task produces no code other services depend on.

- [ ] **Step 1: Confirm no live config references `backend/`**

Run each of these and confirm the outputs match what's shown (already verified during the architecture audit, re-confirm before touching anything):

```bash
grep -rn "backend/" .github/workflows/*.yml
```
Expected: only `'.github/workflows/backend-deploy.yml'` (the workflow's own path filter, matching itself — not a reference to the `backend/` folder).

```bash
grep -n "backend" docker-compose.yml docker-compose-microservices.yml
```
Expected: no output (root `docker-compose.yml` only defines `mongodb`; `docker-compose-microservices.yml` only defines the `microservices/*` services).

```bash
grep -n "8080" frontend/src/services/api.js
```
Expected: `frontend`'s default API base URL points at `localhost:8080` with an inline comment `// Ensure it ends with /api for the gateway` — confirming the frontend always talks to `api-gateway` (which also listens on 8080), never to the monolith directly.

- [ ] **Step 2: Confirm `backend/` has no controller that the microservices split doesn't already cover**

```bash
diff <(ls backend/src/main/java/com/travel2go/backend/controller/ | sort) \
     <(ls microservices/*/src/main/java/com/travel2go/backend/controller/ | sort -u)
```
Expected: every controller in `backend/` (`AuthController`, `BookingController`, `HolidayPackageController`, `MediaController`, `SettingsController`, `UserController`, `ConfiguratorCategoryController`, `TravelConfigurationController`, `AllocationRuleController`) has a same-named counterpart somewhere under `microservices/`. Note that `microservices/booking-service` additionally has `LeadController`, which `backend/` does **not** — the microservices split is strictly ahead of the monolith, never behind it.

- [ ] **Step 3: Stop and get explicit user confirmation before deleting**

This is a large, hard-to-reverse deletion (an entire directory tree). **Do not proceed past this point without the user explicitly confirming.** Present Steps 1–2's findings and ask: *"`backend/` is unreferenced by CI, docker-compose, and the frontend, and every one of its controllers exists (or is superseded) in `microservices/`. Confirm deletion?"*

- [ ] **Step 4: Delete and commit**

```bash
git rm -r backend/
git status
```
Expected: `backend/` no longer appears in `git status`; `git log -- backend/` still shows its history (deletion via `git rm`, not filesystem `rm`, preserves history).

```bash
git commit -m "chore: retire backend/ monolith, superseded by microservices/ split"
```

- [ ] **Step 5: Verify nothing else broke**

```bash
grep -rln "com.travel2go.backend" --include=pom.xml . | xargs grep -l "artifactId>backend<"
```
Expected: no output — no other `pom.xml` declares a dependency on an artifact literally named `backend` (the monolith was never consumed as a library).

---

### Task 2: Add `Trip`, `Leg`, `Traveller`, `TravellerGroup` to `common-models`

**Files:**
- Create: `microservices/common-models/src/main/java/com/travel2go/backend/model/Trip.java`
- Create: `microservices/common-models/src/main/java/com/travel2go/backend/model/Leg.java`
- Create: `microservices/common-models/src/main/java/com/travel2go/backend/model/Traveller.java`
- Create: `microservices/common-models/src/main/java/com/travel2go/backend/model/TravellerGroup.java`
- Test: `microservices/common-models/src/test/java/com/travel2go/backend/model/NewModelsTest.java`

**Interfaces:**
- Produces: `Trip{id, ownerUserId, title, status, travellerIds, legIds, origin, destination, startDate, endDate, budgetPaise, savingsLedgerPaise, createdAt, updatedAt}` (all Lombok `@Builder`/`@Data`, Firestore `@Document(collectionName = "trips")`).
- Produces: `Leg{id, tripId, type, status, supplierRef, pnr, confirmationProbability, pricePaise, quoteToken, startAt, endAt, metadata}` (`@Document(collectionName = "legs")`).
- Produces: `Traveller{id, ownerUserId, fullName, dateOfBirth, idType, idReferenceToken, gender, relationship, isMinor}` (`@Document(collectionName = "travellers")`).
- Produces: `TravellerGroup{id, ownerUserId, name, travellerIds}` (`@Document(collectionName = "traveller_groups")`).
- Consumed by: Task 3 (`trip-service`), Task 5 (`booking-service`'s `LegBookingController` references `Leg`'s `tripId` conceptually via the new DTOs).

- [ ] **Step 1: Write the failing test**

Create `microservices/common-models/src/test/java/com/travel2go/backend/model/NewModelsTest.java`:

```java
package com.travel2go.backend.model;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewModelsTest {

    @Test
    void tripBuilderRoundTrips() {
        Date now = new Date();
        Trip trip = Trip.builder()
                .id("trip-1")
                .ownerUserId("user-1")
                .title("Goa Family Trip")
                .status("DRAFT")
                .travellerIds(List.of("trav-1", "trav-2"))
                .legIds(List.of())
                .origin("Jhansi")
                .destination("Goa")
                .startDate(now)
                .endDate(now)
                .budgetPaise(5000000L)
                .savingsLedgerPaise(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(trip.getId()).isEqualTo("trip-1");
        assertThat(trip.getStatus()).isEqualTo("DRAFT");
        assertThat(trip.getTravellerIds()).containsExactly("trav-1", "trav-2");
    }

    @Test
    void legBuilderRoundTrips() {
        Leg leg = Leg.builder()
                .id("leg-1")
                .tripId("trip-1")
                .type("RAIL")
                .status("SELECTED")
                .pricePaise(150000L)
                .quoteToken("token-abc")
                .metadata(Map.of("trainNumber", "12345"))
                .build();

        assertThat(leg.getTripId()).isEqualTo("trip-1");
        assertThat(leg.getType()).isEqualTo("RAIL");
        assertThat(leg.getMetadata()).containsEntry("trainNumber", "12345");
    }

    @Test
    void travellerBuilderRoundTrips() {
        Traveller traveller = Traveller.builder()
                .id("trav-1")
                .ownerUserId("user-1")
                .fullName("Rajesh Kumar")
                .idType("AADHAAR")
                .idReferenceToken("tok-ref-001")
                .relationship("SELF")
                .isMinor(false)
                .build();

        assertThat(traveller.getFullName()).isEqualTo("Rajesh Kumar");
        assertThat(traveller.getIdReferenceToken()).isEqualTo("tok-ref-001");
        assertThat(traveller.getIsMinor()).isFalse();
    }

    @Test
    void travellerGroupBuilderRoundTrips() {
        TravellerGroup group = TravellerGroup.builder()
                .id("group-1")
                .ownerUserId("user-1")
                .name("Kumar Family")
                .travellerIds(List.of("trav-1", "trav-2", "trav-3"))
                .build();

        assertThat(group.getTravellerIds()).hasSize(3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd microservices/common-models && ./mvnw -q test -Dtest=NewModelsTest`
Expected: FAIL — compilation error, `Trip`/`Leg`/`Traveller`/`TravellerGroup` do not exist yet.

- [ ] **Step 3: Create the four model classes**

Create `microservices/common-models/src/main/java/com/travel2go/backend/model/Trip.java`:

```java
package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "trips")
public class Trip {
    @DocumentId
    private String id;

    private String ownerUserId;
    private String title;
    private String status; // DRAFT | BOOKED | IN_PROGRESS | COMPLETED | CANCELLED

    private List<String> travellerIds;
    private List<String> legIds;

    private String origin;
    private String destination;

    private Date startDate;
    private Date endDate;

    private Long budgetPaise;
    private Long savingsLedgerPaise;

    private Date createdAt;
    private Date updatedAt;
}
```

Create `microservices/common-models/src/main/java/com/travel2go/backend/model/Leg.java`:

```java
package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "legs")
public class Leg {
    @DocumentId
    private String id;

    private String tripId;
    private String type; // RAIL | BUS | HOTEL | FLIGHT | PACKAGE
    private String status; // SEARCHING | SELECTED | WAITLISTED | CONFIRMED | CANCELLED | COMPLETED | DISRUPTED

    private String supplierRef;
    private String pnr;
    private Double confirmationProbability;

    private Long pricePaise;
    private String quoteToken;

    private Date startAt;
    private Date endAt;

    private Map<String, Object> metadata;
}
```

Create `microservices/common-models/src/main/java/com/travel2go/backend/model/Traveller.java`:

```java
package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "travellers")
public class Traveller {
    @DocumentId
    private String id;

    private String ownerUserId;
    private String fullName;
    private Date dateOfBirth;

    private String idType; // AADHAAR | PASSPORT | DL | OTHER
    private String idReferenceToken; // tokenised reference only — never the raw ID (DPDP)

    private String gender;
    private String relationship; // SELF | SPOUSE | CHILD | PARENT | OTHER
    private Boolean isMinor;
}
```

Create `microservices/common-models/src/main/java/com/travel2go/backend/model/TravellerGroup.java`:

```java
package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "traveller_groups")
public class TravellerGroup {
    @DocumentId
    private String id;

    private String ownerUserId;
    private String name;
    private List<String> travellerIds;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd microservices/common-models && ./mvnw -q test -Dtest=NewModelsTest`
Expected: PASS — 4 tests, 0 failures.

- [ ] **Step 5: Install to local Maven repo so downstream services can consume it**

Run: `cd microservices/common-models && ./mvnw -q clean install -DskipTests`
Expected: `BUILD SUCCESS`, `common-models-0.0.1-SNAPSHOT.jar` installed to `~/.m2/repository/com/travel2go/common-models/`.

- [ ] **Step 6: Commit**

```bash
git add microservices/common-models/src/main/java/com/travel2go/backend/model/Trip.java \
        microservices/common-models/src/main/java/com/travel2go/backend/model/Leg.java \
        microservices/common-models/src/main/java/com/travel2go/backend/model/Traveller.java \
        microservices/common-models/src/main/java/com/travel2go/backend/model/TravellerGroup.java \
        microservices/common-models/src/test/java/com/travel2go/backend/model/NewModelsTest.java
git commit -m "feat(common-models): add Trip, Leg, Traveller, TravellerGroup domain models"
```

---

### Task 3: Scaffold `trip-service` with Trip/Leg CRUD + `trip.exchange`

**Files:**
- Create: `microservices/trip-service/pom.xml`, `Dockerfile`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/TripServiceApplication.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/config/FirestoreConfig.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/security/{JwtUtil,JwtAuthenticationFilter,SecurityConfig}.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/repository/{TripRepository,LegRepository}.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/service/{TripEventPublisher,TripService}.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/dto/{CreateTripRequest,AddLegRequest,TripDetailResponse}.java`
- Create: `microservices/trip-service/src/main/resources/application.properties`
- Test: `microservices/trip-service/src/test/java/com/travel2go/backend/{TripServiceApplicationTests,service/TripServiceTest}.java`
- Modify: `docker-compose-microservices.yml`
- Modify: `microservices/api-gateway/src/main/resources/application.properties`
- Modify: `.github/workflows/backend-deploy.yml`

**Interfaces:**
- Consumes: `Trip`, `Leg` from `common-models` (Task 2).
- Produces: `TripService.createTrip(CreateTripRequest): Trip`, `TripService.getTripDetail(String tripId): TripDetailResponse`, `TripService.addLeg(String tripId, AddLegRequest): Leg` — `bookLeg(...)` is added in Task 5. `TripEventPublisher.publish(String routingKey, Object payload): void`.

- [ ] **Step 1: Scaffold the Maven module**

```bash
mkdir -p microservices/trip-service/src/main/java/com/travel2go/backend/config
mkdir -p microservices/trip-service/src/main/java/com/travel2go/backend/security
mkdir -p microservices/trip-service/src/main/java/com/travel2go/backend/repository
mkdir -p microservices/trip-service/src/main/java/com/travel2go/backend/service
mkdir -p microservices/trip-service/src/main/java/com/travel2go/backend/controller
mkdir -p microservices/trip-service/src/main/java/com/travel2go/backend/dto
mkdir -p microservices/trip-service/src/main/resources
mkdir -p microservices/trip-service/src/test/java/com/travel2go/backend/service
cp -r microservices/booking-service/.mvn microservices/trip-service/.mvn
cp microservices/booking-service/mvnw microservices/trip-service/mvnw
cp microservices/booking-service/mvnw.cmd microservices/trip-service/mvnw.cmd
chmod +x microservices/trip-service/mvnw
```

Create `microservices/trip-service/Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `microservices/trip-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.2.4</version>
		<relativePath/>
	</parent>
	<groupId>com.travel2go</groupId>
	<artifactId>trip-service</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>trip-service</name>
	<description>Owns the Trip/Leg lifecycle - the journey spine</description>
	<properties>
		<java.version>17</java.version>
		<lombok.version>1.18.38</lombok.version>
		<spring-cloud.version>2023.0.1</spring-cloud.version>
	</properties>
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
			<dependency>
				<groupId>com.google.cloud</groupId>
				<artifactId>spring-cloud-gcp-dependencies</artifactId>
				<version>5.1.0</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
	<dependencies>
		<dependency>
			<groupId>com.google.cloud</groupId>
			<artifactId>spring-cloud-gcp-starter-data-firestore</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-amqp</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.cloud</groupId>
			<artifactId>spring-cloud-starter-openfeign</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>com.travel2go</groupId>
			<artifactId>common-models</artifactId>
			<version>0.0.1-SNAPSHOT</version>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-api</artifactId>
			<version>0.11.5</version>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-impl</artifactId>
			<version>0.11.5</version>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-jackson</artifactId>
			<version>0.11.5</version>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>
	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
				<configuration>
					<excludes>
						<exclude>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</exclude>
					</excludes>
				</configuration>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<source>${java.version}</source>
					<target>${java.version}</target>
					<annotationProcessorPaths>
						<path>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
							<version>${lombok.version}</version>
						</path>
					</annotationProcessorPaths>
				</configuration>
			</plugin>
		</plugins>
	</build>
</project>
```

- [ ] **Step 2: Application entry point, Firestore + RabbitMQ config**

Create `microservices/trip-service/src/main/java/com/travel2go/backend/TripServiceApplication.java`:

```java
package com.travel2go.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TripServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TripServiceApplication.class, args);
	}
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/config/FirestoreConfig.java`:

```java
package com.travel2go.backend.config;

import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirestoreConfig {

    @Value("${spring.cloud.gcp.firestore.project-id:travel2go-495007}")
    private String projectId;

    @Value("${spring.cloud.gcp.firestore.database-id:travel2go-db}")
    private String databaseId;

    @Bean
    public FirestoreOptions firestoreOptions() {
        return FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setDatabaseId(databaseId)
                .build();
    }
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`:

```java
package com.travel2go.backend.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Autowired
    public void configureVirtualHost(ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory) {
            CachingConnectionFactory cachingFactory = (CachingConnectionFactory) connectionFactory;
            if ("/".equals(cachingFactory.getVirtualHost()) && !"guest".equals(cachingFactory.getUsername())) {
                cachingFactory.setVirtualHost(cachingFactory.getUsername());
            }
        }
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/config/FeignConfig.java`:

```java
package com.travel2go.backend.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null) {
                template.header("Authorization", authorizationHeader);
            }
        }
    }
}
```

- [ ] **Step 3: JWT security (copied pattern, minimal matcher)**

Create `microservices/trip-service/src/main/java/com/travel2go/backend/security/JwtUtil.java` (identical to `booking-service`'s):

```java
package com.travel2go.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public List<SimpleGrantedAuthority> extractRoles(String token) {
        final Claims claims = extractAllClaims(token);
        java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();

        String role = claims.get("role", String.class);
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
        }

        java.util.List<String> roles = claims.get("roles", java.util.List.class);
        if (roles != null) {
            for (String r : roles) {
                authorities.add(new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r));
            }
        }
        return authorities;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/security/JwtAuthenticationFilter.java` (identical to `booking-service`'s):

```java
package com.travel2go.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtUtil.extractUsername(jwt);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                org.springframework.security.core.userdetails.User userDetails =
                        new org.springframework.security.core.userdetails.User(username, "", jwtUtil.extractRoles(jwt));
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token validation failed - request proceeds unauthenticated
        }
        filterChain.doFilter(request, response);
    }
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/security/SecurityConfig.java`:

```java
package com.travel2go.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Unauthorized\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Forbidden\"}");
                        })
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173", "https://travel2go-495007.web.app", "https://travel2go.in"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

- [ ] **Step 4: Repositories, event publisher, DTOs**

Create `microservices/trip-service/src/main/java/com/travel2go/backend/repository/TripRepository.java`:

```java
package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.Trip;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends FirestoreReactiveRepository<Trip> {
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/repository/LegRepository.java`:

```java
package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.Leg;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface LegRepository extends FirestoreReactiveRepository<Leg> {
    Flux<Leg> findByTripId(String tripId);
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripEventPublisher.java`:

```java
package com.travel2go.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripEventPublisher {

    public static final String EXCHANGE = "trip.exchange";

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
    }
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/dto/CreateTripRequest.java`:

```java
package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTripRequest {
    private String title;
    private List<String> travellerIds;
    private String origin;
    private String destination;
    private Date startDate;
    private Date endDate;
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/dto/AddLegRequest.java`:

```java
package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddLegRequest {
    private String type;
    private Long pricePaise;
    private String quoteToken;
    private Date startAt;
    private Date endAt;
    private Map<String, Object> metadata;
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/dto/TripDetailResponse.java`:

```java
package com.travel2go.backend.dto;

import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailResponse {
    private Trip trip;
    private List<Leg> legs;
}
```

- [ ] **Step 5: Write the failing test for `TripService`**

Create `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java`:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.client.BookingClient;
import com.travel2go.backend.dto.AddLegRequest;
import com.travel2go.backend.dto.CreateTripRequest;
import com.travel2go.backend.dto.TripDetailResponse;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private LegRepository legRepository;
    @Mock private BookingClient bookingClient;
    @Mock private TripEventPublisher eventPublisher;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, legRepository, bookingClient, eventPublisher);
    }

    @Test
    void createTrip_savesDraftTripAndPublishesEvent() {
        CreateTripRequest request = CreateTripRequest.builder()
                .title("Goa Family Trip")
                .travellerIds(List.of("trav-1", "trav-2"))
                .origin("Jhansi")
                .destination("Goa")
                .build();

        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId("trip-1");
            return Mono.just(t);
        });

        Trip result = tripService.createTrip(request);

        assertThat(result.getId()).isEqualTo("trip-1");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(eventPublisher).publish(eq("trip.created"), any());
    }

    @Test
    void getTripDetail_returnsTripWithItsLegs() {
        Trip trip = Trip.builder().id("trip-1").title("Goa Family Trip").build();
        Leg leg = Leg.builder().id("leg-1").tripId("trip-1").type("RAIL").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.findByTripId("trip-1")).thenReturn(Flux.just(leg));

        TripDetailResponse result = tripService.getTripDetail("trip-1");

        assertThat(result.getTrip().getId()).isEqualTo("trip-1");
        assertThat(result.getLegs()).hasSize(1);
        assertThat(result.getLegs().get(0).getId()).isEqualTo("leg-1");
    }

    @Test
    void getTripDetail_throwsWhenTripMissing() {
        when(tripRepository.findById("missing")).thenReturn(Mono.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tripService.getTripDetail("missing"));
    }

    @Test
    void addLeg_appendsLegIdToTripAndSavesLeg() {
        Trip trip = Trip.builder().id("trip-1").legIds(new java.util.ArrayList<>()).build();
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
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Leg result = tripService.addLeg("trip-1", request);

        assertThat(result.getId()).isEqualTo("leg-1");
        assertThat(result.getStatus()).isEqualTo("SELECTED");
        assertThat(trip.getLegIds()).containsExactly("leg-1");
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd microservices/trip-service && ./mvnw -q test -Dtest=TripServiceTest`
Expected: FAIL — compilation error, `TripService` and `BookingClient` don't exist yet.

- [ ] **Step 7: Create a placeholder `BookingClient` (full implementation lands in Task 5) and `TripService`**

Create `microservices/trip-service/src/main/java/com/travel2go/backend/client/BookingClient.java`:

```java
package com.travel2go.backend.client;

import org.springframework.cloud.openfeign.FeignClient;

// Full leg-booking method added in Task 5, once booking-service exposes POST /api/leg-bookings.
@FeignClient(name = "booking-service", url = "${BOOKING_SERVICE_URL:http://localhost:8083}",
        configuration = com.travel2go.backend.config.FeignConfig.class)
public interface BookingClient {
}
```

Create `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.client.BookingClient;
import com.travel2go.backend.dto.AddLegRequest;
import com.travel2go.backend.dto.CreateTripRequest;
import com.travel2go.backend.dto.TripDetailResponse;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final LegRepository legRepository;
    private final BookingClient bookingClient;
    private final TripEventPublisher eventPublisher;

    public Trip createTrip(CreateTripRequest request) {
        Date now = new Date();
        Trip trip = Trip.builder()
                .title(request.getTitle())
                .travellerIds(request.getTravellerIds())
                .legIds(new ArrayList<>())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status("DRAFT")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Trip saved = tripRepository.save(trip).block();

        eventPublisher.publish("trip.created", Map.of("tripId", saved.getId()));

        return saved;
    }

    public TripDetailResponse getTripDetail(String tripId) {
        Trip trip = tripRepository.findById(tripId).block();
        if (trip == null) {
            throw new IllegalArgumentException("Trip not found: " + tripId);
        }
        List<Leg> legs = legRepository.findByTripId(tripId).collectList().block();
        return new TripDetailResponse(trip, legs);
    }

    public Leg addLeg(String tripId, AddLegRequest request) {
        Trip trip = tripRepository.findById(tripId).block();
        if (trip == null) {
            throw new IllegalArgumentException("Trip not found: " + tripId);
        }

        Leg leg = Leg.builder()
                .tripId(tripId)
                .type(request.getType())
                .status("SELECTED")
                .pricePaise(request.getPricePaise())
                .quoteToken(request.getQuoteToken())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .metadata(request.getMetadata())
                .build();

        Leg savedLeg = legRepository.save(leg).block();

        trip.getLegIds().add(savedLeg.getId());
        trip.setUpdatedAt(new Date());
        tripRepository.save(trip).block();

        return savedLeg;
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd microservices/trip-service && ./mvnw -q test -Dtest=TripServiceTest`
Expected: PASS — 4 tests, 0 failures.

- [ ] **Step 9: Controller, `application.properties`, application context test**

Create `microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java`:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.dto.AddLegRequest;
import com.travel2go.backend.dto.CreateTripRequest;
import com.travel2go.backend.dto.TripDetailResponse;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import com.travel2go.backend.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<Trip> createTrip(@RequestBody CreateTripRequest request) {
        return ResponseEntity.ok(tripService.createTrip(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDetailResponse> getTrip(@PathVariable String id) {
        return ResponseEntity.ok(tripService.getTripDetail(id));
    }

    @PostMapping("/{id}/legs")
    public ResponseEntity<Leg> addLeg(@PathVariable String id, @RequestBody AddLegRequest request) {
        return ResponseEntity.ok(tripService.addLeg(id, request));
    }
}
```

Create `microservices/trip-service/src/main/resources/application.properties`:

```properties
spring.application.name=trip-service
server.port=${PORT:8080}
server.forward-headers-strategy=framework
spring.cloud.gcp.firestore.project-id=${PROJECT_ID:travel2go-495007}
spring.cloud.gcp.firestore.database-id=travel2go-db

jwt.secret=${JWT_SECRET:94a08da1fecbb6e8b46990538c7b50b294a08da1fecbb6e8b46990538c7b50b2}
jwt.expiration=86400000

spring.rabbitmq.host=${SPRING_RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${SPRING_RABBITMQ_PORT:5672}
spring.rabbitmq.username=${SPRING_RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${SPRING_RABBITMQ_PASSWORD:guest}
spring.rabbitmq.virtual-host=${SPRING_RABBITMQ_VIRTUAL_HOST:/}
spring.rabbitmq.ssl.enabled=${SPRING_RABBITMQ_SSL_ENABLED:false}

management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always
```

Create `microservices/trip-service/src/test/java/com/travel2go/backend/TripServiceApplicationTests.java`:

```java
package com.travel2go.backend;

import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockBean({TripRepository.class, LegRepository.class})
class TripServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
```

Run: `cd microservices/trip-service && ./mvnw -q test`
Expected: PASS — all tests including `contextLoads`.

- [ ] **Step 10: Wire into the gateway, docker-compose, and CI matrix**

In `microservices/api-gateway/src/main/resources/application.properties`, append after the existing `routes[7]` block:

```properties
spring.cloud.gateway.routes[8].id=trip-service
spring.cloud.gateway.routes[8].uri=${TRIP_SERVICE_URL:http://localhost:8087}
spring.cloud.gateway.routes[8].predicates[0]=Path=/api/trips/**
```

In `docker-compose-microservices.yml`, replace the `api-gateway` service block with this expanded version (adds `TRIP_SERVICE_URL` and a new dependency):

```yaml
  api-gateway:
    build: ./microservices/api-gateway
    ports:
      - "8080:8080"
    networks:
      - travel2go-network
    depends_on:
      - identity-service
      - package-service
      - booking-service
      - media-service
      - notification-service
      - trip-service
    environment:
      - IDENTITY_SERVICE_URL=http://identity-service:8081
      - PACKAGE_SERVICE_URL=http://package-service:8082
      - BOOKING_SERVICE_URL=http://booking-service:8083
      - MEDIA_SERVICE_URL=http://media-service:8084
      - TRIP_SERVICE_URL=http://trip-service:8087
```

Then add a new `trip-service` block right after the `notification-service` block (before `frontend`):

```yaml
  trip-service:
    build: ./microservices/trip-service
    ports:
      - "8087:8087"
    networks:
      - travel2go-network
    depends_on:
      - rabbitmq
      - booking-service
    environment:
      - SERVER_PORT=8087
      - BOOKING_SERVICE_URL=http://booking-service:8083
      - SPRING_RABBITMQ_HOST=rabbitmq
```

In `.github/workflows/backend-deploy.yml`, add `trip-service` to the matrix `include` list (after `reactive-booking-function`):

```yaml
          - service: reactive-booking-function
          - service: trip-service
```

- [ ] **Step 11: Commit**

```bash
git add microservices/trip-service docker-compose-microservices.yml \
        microservices/api-gateway/src/main/resources/application.properties \
        .github/workflows/backend-deploy.yml
git commit -m "feat: scaffold trip-service with Trip/Leg CRUD and trip.exchange"
```

---

### Task 4: Scaffold `payment-service` with UPI provider (sandbox) + `quoteToken` validation

**Files:**
- Create: `microservices/payment-service/pom.xml`, `Dockerfile`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/PaymentServiceApplication.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/config/FirestoreConfig.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/security/{JwtUtil,JwtAuthenticationFilter,SecurityConfig}.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/service/{QuoteTokenService,PaymentService}.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/{PaymentProvider,PaymentResult,SandboxUpiProvider}.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/dto/ChargeRequest.java`
- Create: `microservices/payment-service/src/main/resources/application.properties`
- Test: `microservices/payment-service/src/test/java/com/travel2go/backend/{PaymentServiceApplicationTests,service/{QuoteTokenServiceTest,PaymentServiceTest}}.java`
- Create: `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`
- Modify: `docker-compose-microservices.yml`, `microservices/api-gateway/src/main/resources/application.properties`, `.github/workflows/backend-deploy.yml`

**Interfaces:**
- Produces: `Payment` model (common-models). `QuoteTokenService.issue(String legId, long pricePaise): String`, `QuoteTokenService.isValid(String token, String legId, long pricePaise): boolean`. `PaymentService.charge(String bookingRef, long amountPaise, String method, String quoteToken): Payment`. `PaymentProvider.charge(String reference, long amountPaise, String method): PaymentResult`.

- [ ] **Step 1: Add the `Payment` model to `common-models`**

Create `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`:

```java
package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "payments")
public class Payment {
    @DocumentId
    private String id;

    private String bookingRef;
    private String method; // UPI | CARD | NETBANKING
    private String status; // SUCCESS | FAILED

    private Long amountPaise;
    private Long feePaise; // MUST always be 0 (G1 - zero booking/convenience fee)

    private String providerRef;
    private Boolean quoteTokenValidated;

    private Date createdAt;
}
```

Run: `cd microservices/common-models && ./mvnw -q clean install -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Scaffold the Maven module (same pattern as Task 3, Step 1)**

```bash
mkdir -p microservices/payment-service/src/main/java/com/travel2go/backend/config
mkdir -p microservices/payment-service/src/main/java/com/travel2go/backend/security
mkdir -p microservices/payment-service/src/main/java/com/travel2go/backend/service
mkdir -p microservices/payment-service/src/main/java/com/travel2go/backend/provider
mkdir -p microservices/payment-service/src/main/java/com/travel2go/backend/repository
mkdir -p microservices/payment-service/src/main/java/com/travel2go/backend/controller
mkdir -p microservices/payment-service/src/main/java/com/travel2go/backend/dto
mkdir -p microservices/payment-service/src/main/resources
mkdir -p microservices/payment-service/src/test/java/com/travel2go/backend/service
cp -r microservices/booking-service/.mvn microservices/payment-service/.mvn
cp microservices/booking-service/mvnw microservices/payment-service/mvnw
cp microservices/booking-service/mvnw.cmd microservices/payment-service/mvnw.cmd
chmod +x microservices/payment-service/mvnw
```

Create `microservices/payment-service/Dockerfile` (identical to `trip-service`'s):

```dockerfile
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `microservices/payment-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.2.4</version>
		<relativePath/>
	</parent>
	<groupId>com.travel2go</groupId>
	<artifactId>payment-service</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>payment-service</name>
	<description>UPI-first payments with quote-token validation</description>
	<properties>
		<java.version>17</java.version>
		<lombok.version>1.18.38</lombok.version>
		<spring-cloud.version>2023.0.1</spring-cloud.version>
	</properties>
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
			<dependency>
				<groupId>com.google.cloud</groupId>
				<artifactId>spring-cloud-gcp-dependencies</artifactId>
				<version>5.1.0</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
	<dependencies>
		<dependency>
			<groupId>com.google.cloud</groupId>
			<artifactId>spring-cloud-gcp-starter-data-firestore</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>
		<dependency>
			<groupId>com.travel2go</groupId>
			<artifactId>common-models</artifactId>
			<version>0.0.1-SNAPSHOT</version>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-api</artifactId>
			<version>0.11.5</version>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-impl</artifactId>
			<version>0.11.5</version>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-jackson</artifactId>
			<version>0.11.5</version>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>
	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
				<configuration>
					<excludes>
						<exclude>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
						</exclude>
					</excludes>
				</configuration>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<source>${java.version}</source>
					<target>${java.version}</target>
					<annotationProcessorPaths>
						<path>
							<groupId>org.projectlombok</groupId>
							<artifactId>lombok</artifactId>
							<version>${lombok.version}</version>
						</path>
					</annotationProcessorPaths>
				</configuration>
			</plugin>
		</plugins>
	</build>
</project>
```

- [ ] **Step 3: Write the failing test for `QuoteTokenService`**

Create `microservices/payment-service/src/test/java/com/travel2go/backend/service/QuoteTokenServiceTest.java`:

```java
package com.travel2go.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteTokenServiceTest {

    private QuoteTokenService quoteTokenService;

    @BeforeEach
    void setUp() {
        quoteTokenService = new QuoteTokenService();
        ReflectionTestUtils.setField(quoteTokenService, "secret", "test-secret-key-for-quote-tokens-1234567890");
        ReflectionTestUtils.setField(quoteTokenService, "ttlMs", 900000L);
    }

    @Test
    void issuedTokenValidatesForSameLegAndPrice() {
        String token = quoteTokenService.issue("leg-1", 150000L);

        assertThat(quoteTokenService.isValid(token, "leg-1", 150000L)).isTrue();
    }

    @Test
    void tokenRejectedWhenPriceChanged() {
        String token = quoteTokenService.issue("leg-1", 150000L);

        assertThat(quoteTokenService.isValid(token, "leg-1", 150001L)).isFalse();
    }

    @Test
    void tokenRejectedWhenLegIdChanged() {
        String token = quoteTokenService.issue("leg-1", 150000L);

        assertThat(quoteTokenService.isValid(token, "leg-2", 150000L)).isFalse();
    }

    @Test
    void tamperedTokenRejected() {
        String token = quoteTokenService.issue("leg-1", 150000L);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(quoteTokenService.isValid(tampered, "leg-1", 150000L)).isFalse();
    }

    @Test
    void expiredTokenRejected() {
        ReflectionTestUtils.setField(quoteTokenService, "ttlMs", -1000L);
        String token = quoteTokenService.issue("leg-1", 150000L);

        assertThat(quoteTokenService.isValid(token, "leg-1", 150000L)).isFalse();
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=QuoteTokenServiceTest`
Expected: FAIL — `QuoteTokenService` does not exist yet.

- [ ] **Step 5: Implement `QuoteTokenService`**

Create `microservices/payment-service/src/main/java/com/travel2go/backend/service/QuoteTokenService.java`:

```java
package com.travel2go.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class QuoteTokenService {

    @Value("${quote.token.secret}")
    private String secret;

    @Value("${quote.token.ttl-ms}")
    private long ttlMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String legId, long pricePaise) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(legId)
                .claim("pricePaise", pricePaise)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isValid(String token, String legId, long pricePaise) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            boolean legMatches = legId.equals(claims.getSubject());
            Number priceClaim = claims.get("pricePaise", Number.class);
            boolean priceMatches = priceClaim != null && pricePaise == priceClaim.longValue();
            return legMatches && priceMatches;
        } catch (ExpiredJwtException | JwtException e) {
            return false;
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=QuoteTokenServiceTest`
Expected: PASS — 5 tests, 0 failures.

- [ ] **Step 7: Write the failing test for `PaymentService`**

Create `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.PaymentResult;
import com.travel2go.backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, paymentProvider, quoteTokenService);
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void charge_succeedsAndNeverAddsAFee() {
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.charge("leg-1", 150000L, "UPI"))
                .thenReturn(new PaymentResult("SUCCESS", "SANDBOX-abc123"));

        Payment result = paymentService.charge("leg-1", 150000L, "UPI", "valid-token");

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        assertThat(result.getProviderRef()).isEqualTo("SANDBOX-abc123");
    }

    @Test
    void charge_rejectsWhenQuoteTokenInvalid_withoutCallingProvider() {
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        Payment result = paymentService.charge("leg-1", 150000L, "UPI", "bad-token");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        verify(paymentProvider, never()).charge(any(), anyLong(), any());
    }
}
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentServiceTest`
Expected: FAIL — `PaymentService`, `PaymentProvider`, `PaymentResult`, `PaymentRepository` don't exist yet.

- [ ] **Step 9: Implement the provider interface, sandbox implementation, repository, and `PaymentService`**

Create `microservices/payment-service/src/main/java/com/travel2go/backend/provider/PaymentResult.java`:

```java
package com.travel2go.backend.provider;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PaymentResult {
    String status; // SUCCESS | FAILED
    String providerRef;
}
```

Create `microservices/payment-service/src/main/java/com/travel2go/backend/provider/PaymentProvider.java`:

```java
package com.travel2go.backend.provider;

public interface PaymentProvider {
    PaymentResult charge(String reference, long amountPaise, String method);
}
```

Create `microservices/payment-service/src/main/java/com/travel2go/backend/provider/SandboxUpiProvider.java`:

```java
package com.travel2go.backend.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SandboxUpiProvider implements PaymentProvider {

    // Stubbed pending a real UPI provider decision (Razorpay vs Cashfree) - see OPEN_QUESTIONS.md.
    // Always succeeds so trip-booking and refund flows can be built and tested end-to-end now.
    @Override
    public PaymentResult charge(String reference, long amountPaise, String method) {
        return new PaymentResult("SUCCESS", "SANDBOX-" + UUID.randomUUID());
    }
}
```

Create `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java`:

```java
package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.Payment;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends FirestoreReactiveRepository<Payment> {
}
```

Create `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.PaymentResult;
import com.travel2go.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final QuoteTokenService quoteTokenService;

    public Payment charge(String bookingRef, long amountPaise, String method, String quoteToken) {
        boolean quoteValid = quoteTokenService.isValid(quoteToken, bookingRef, amountPaise);

        if (!quoteValid) {
            Payment rejected = Payment.builder()
                    .bookingRef(bookingRef)
                    .method(method)
                    .status("FAILED")
                    .amountPaise(amountPaise)
                    .feePaise(0L)
                    .quoteTokenValidated(false)
                    .createdAt(new Date())
                    .build();
            return paymentRepository.save(rejected).block();
        }

        PaymentResult result = paymentProvider.charge(bookingRef, amountPaise, method);

        Payment payment = Payment.builder()
                .bookingRef(bookingRef)
                .method(method)
                .status(result.getStatus())
                .amountPaise(amountPaise)
                .feePaise(0L)
                .providerRef(result.getProviderRef())
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();

        return paymentRepository.save(payment).block();
    }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentServiceTest`
Expected: PASS — 2 tests, 0 failures.

- [ ] **Step 11: Controller, security, application entry point, properties, context test**

Create `microservices/payment-service/src/main/java/com/travel2go/backend/dto/ChargeRequest.java`:

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
public class ChargeRequest {
    private String bookingRef;
    private Long amountPaise;
    private String method;
    private String quoteToken;
}
```

Create `microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java`:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.dto.ChargeRequest;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Payment> charge(@RequestBody ChargeRequest request) {
        Payment payment = paymentService.charge(
                request.getBookingRef(), request.getAmountPaise(), request.getMethod(), request.getQuoteToken());

        if ("FAILED".equals(payment.getStatus())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(payment);
        }
        return ResponseEntity.ok(payment);
    }
}
```

Create `microservices/payment-service/src/main/java/com/travel2go/backend/PaymentServiceApplication.java`:

```java
package com.travel2go.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}
}
```

Copy `microservices/trip-service/src/main/java/com/travel2go/backend/config/FirestoreConfig.java` and the three `security/` classes (`JwtUtil.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`) verbatim into the equivalent `microservices/payment-service/src/main/java/com/travel2go/backend/{config,security}/` paths — they are service-agnostic.

Create `microservices/payment-service/src/main/resources/application.properties`:

```properties
spring.application.name=payment-service
server.port=${PORT:8080}
server.forward-headers-strategy=framework
spring.cloud.gcp.firestore.project-id=${PROJECT_ID:travel2go-495007}
spring.cloud.gcp.firestore.database-id=travel2go-db

jwt.secret=${JWT_SECRET:94a08da1fecbb6e8b46990538c7b50b294a08da1fecbb6e8b46990538c7b50b2}
jwt.expiration=86400000

quote.token.secret=${QUOTE_TOKEN_SECRET:5f3a8c1e9b7d4f6a2c8e0b1d3f5a7c9e5f3a8c1e9b7d4f6a2c8e0b1d3f5a7c9e}
quote.token.ttl-ms=900000

management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always
```

Create `microservices/payment-service/src/test/java/com/travel2go/backend/PaymentServiceApplicationTests.java`:

```java
package com.travel2go.backend;

import com.travel2go.backend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockBean({PaymentRepository.class})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
```

Run: `cd microservices/payment-service && ./mvnw -q test`
Expected: PASS — all tests including `contextLoads`.

- [ ] **Step 12: Wire into the gateway, docker-compose, and CI matrix**

In `microservices/api-gateway/src/main/resources/application.properties`, append after the `routes[8]` block added in Task 3:

```properties
spring.cloud.gateway.routes[9].id=payment-service
spring.cloud.gateway.routes[9].uri=${PAYMENT_SERVICE_URL:http://localhost:8088}
spring.cloud.gateway.routes[9].predicates[0]=Path=/api/payments/**
```

In `docker-compose-microservices.yml`, add `PAYMENT_SERVICE_URL` to `api-gateway`'s `environment` block (below `TRIP_SERVICE_URL`) and `payment-service` to its `depends_on` list, then add a new `payment-service` block after the `trip-service` block:

```yaml
  payment-service:
    build: ./microservices/payment-service
    ports:
      - "8088:8088"
    networks:
      - travel2go-network
    environment:
      - SERVER_PORT=8088
```

In `.github/workflows/backend-deploy.yml`, add `payment-service` to the matrix `include` list (after `trip-service`):

```yaml
          - service: trip-service
          - service: payment-service
```

- [ ] **Step 13: Commit**

```bash
git add microservices/payment-service microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java \
        docker-compose-microservices.yml microservices/api-gateway/src/main/resources/application.properties \
        .github/workflows/backend-deploy.yml
git commit -m "feat: scaffold payment-service with sandbox UPI provider and quoteToken validation (G2)"
```

---

### Task 5: Repurpose `booking-service` for leg-level bookings orchestrated by `trip-service`

**Files:**
- Create: `microservices/common-models/src/main/java/com/travel2go/backend/dto/{LegBookingRequest,LegBookingResponse}.java`
- Modify: `microservices/common-models/src/main/java/com/travel2go/backend/model/Booking.java`
- Create: `microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java`
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/client/BookingClient.java`
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java`
- Modify: `microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java`
- Create: `microservices/trip-service/src/main/java/com/travel2go/backend/dto/BookLegRequest.java`
- Test: extend `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java`

**Interfaces:**
- Consumes: `BookingClient` (Task 3 placeholder, now completed), `LegRepository`/`TripRepository`/`TripEventPublisher` (Task 3).
- Produces: `TripService.bookLeg(String tripId, String legId, Long amountPaise, String quoteToken): Leg`. `POST /api/leg-bookings` on `booking-service` (internal, service-to-service only — never routed through the gateway).

- [ ] **Step 1: Add shared DTOs to `common-models` and extend `Booking`**

Create `microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingRequest.java`:

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
}
```

Create `microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingResponse.java`:

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
public class LegBookingResponse {
    private String bookingId;
    private String status;
}
```

Modify `microservices/common-models/src/main/java/com/travel2go/backend/model/Booking.java` — add four new nullable fields after `status` (existing fields are untouched, so the legacy `POST /api/bookings` flow in `BookingController` keeps compiling and behaving identically):

```java
package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "bookings")
public class Booking {
    @DocumentId
    private String id;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;

    private String packageId;
    private String packageTitle;

    private Date bookingDate;
    private String status;

    // Leg-level booking fields (trip-service orchestration) - null for legacy package bookings
    private String tripId;
    private String legId;
    private String quoteToken;
    private Long amountPaise;
    private Long feePaise;
}
```

Run: `cd microservices/common-models && ./mvnw -q clean install -DskipTests`
Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Add `POST /api/leg-bookings` to `booking-service`**

Create `microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java`:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/api/leg-bookings")
@RequiredArgsConstructor
public class LegBookingController {

    private final BookingRepository bookingRepository;

    @PostMapping
    public ResponseEntity<LegBookingResponse> createLegBooking(@RequestBody LegBookingRequest request) {
        Booking booking = Booking.builder()
                .tripId(request.getTripId())
                .legId(request.getLegId())
                .quoteToken(request.getQuoteToken())
                .amountPaise(request.getAmountPaise())
                .feePaise(0L)
                .status("CONFIRMED")
                .bookingDate(new Date())
                .build();

        Booking saved = bookingRepository.save(booking).block();

        return ResponseEntity.ok(new LegBookingResponse(saved.getId(), saved.getStatus()));
    }
}
```

No `SecurityConfig` change is needed: `booking-service`'s existing `authorizeHttpRequests` chain ends in `.anyRequest().authenticated()`, which already covers the new `/api/leg-bookings/**` path.

- [ ] **Step 3: Write the failing test for `TripService.bookLeg`**

Append these two tests to `microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java` (inside the existing class, alongside the Task 3 tests):

```java
    @Test
    void bookLeg_confirmsLegAndPublishesEvent() {
        com.travel2go.backend.model.Leg leg = com.travel2go.backend.model.Leg.builder()
                .id("leg-1").tripId("trip-1").status("SELECTED").build();

        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(bookingClient.createLegBooking(any())).thenReturn(
                com.travel2go.backend.dto.LegBookingResponse.builder()
                        .bookingId("booking-99").status("CONFIRMED").build());
        when(legRepository.save(any(com.travel2go.backend.model.Leg.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        com.travel2go.backend.model.Leg result = tripService.bookLeg("trip-1", "leg-1", 150000L, "quote-token-abc");

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getSupplierRef()).isEqualTo("booking-99");
        verify(eventPublisher).publish(eq("leg.booked"), any());
    }

    @Test
    void bookLeg_rejectsLegFromDifferentTrip() {
        com.travel2go.backend.model.Leg leg = com.travel2go.backend.model.Leg.builder()
                .id("leg-1").tripId("trip-OTHER").build();

        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tripService.bookLeg("trip-1", "leg-1", 150000L, "quote-token-abc"));

        verify(bookingClient, never()).createLegBooking(any());
    }
```

Add the missing static import to the top of the test file: `import static org.mockito.Mockito.never;` (alongside the existing `verify`/`when` imports).

- [ ] **Step 4: Run test to verify it fails**

Run: `cd microservices/trip-service && ./mvnw -q test -Dtest=TripServiceTest`
Expected: FAIL — `BookingClient.createLegBooking(...)` and `TripService.bookLeg(...)` don't exist yet.

- [ ] **Step 5: Complete `BookingClient` and add `TripService.bookLeg`**

Replace `microservices/trip-service/src/main/java/com/travel2go/backend/client/BookingClient.java`:

```java
package com.travel2go.backend.client;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "booking-service", url = "${BOOKING_SERVICE_URL:http://localhost:8083}",
        configuration = com.travel2go.backend.config.FeignConfig.class)
public interface BookingClient {

    @PostMapping("/api/leg-bookings")
    LegBookingResponse createLegBooking(@RequestBody LegBookingRequest request);
}
```

Add this method to `microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java` (inside the class, after `addLeg`):

```java
    public Leg bookLeg(String tripId, String legId, Long amountPaise, String quoteToken) {
        Leg leg = legRepository.findById(legId).block();
        if (leg == null || !tripId.equals(leg.getTripId())) {
            throw new IllegalArgumentException("Leg not found for trip: " + legId);
        }

        com.travel2go.backend.dto.LegBookingResponse response = bookingClient.createLegBooking(
                com.travel2go.backend.dto.LegBookingRequest.builder()
                        .tripId(tripId)
                        .legId(legId)
                        .quoteToken(quoteToken)
                        .amountPaise(amountPaise)
                        .build());

        leg.setStatus("CONFIRMED");
        leg.setSupplierRef(response.getBookingId());
        Leg saved = legRepository.save(leg).block();

        eventPublisher.publish("leg.booked", Map.of(
                "tripId", tripId,
                "legId", legId,
                "bookingId", response.getBookingId()
        ));

        return saved;
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd microservices/trip-service && ./mvnw -q test -Dtest=TripServiceTest`
Expected: PASS — 6 tests, 0 failures.

- [ ] **Step 7: Expose `bookLeg` on `TripController`**

Create `microservices/trip-service/src/main/java/com/travel2go/backend/dto/BookLegRequest.java`:

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
    private Long amountPaise;
    private String quoteToken;
}
```

Add this endpoint to `microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java` (inside the class, after `addLeg`), and add the `BookLegRequest` import at the top:

```java
    @PostMapping("/{id}/legs/{legId}/book")
    public ResponseEntity<Leg> bookLeg(@PathVariable String id, @PathVariable String legId,
                                        @RequestBody BookLegRequest request) {
        return ResponseEntity.ok(tripService.bookLeg(id, legId, request.getAmountPaise(), request.getQuoteToken()));
    }
```

- [ ] **Step 8: Run the full test suite for both services**

Run: `cd microservices/trip-service && ./mvnw -q test`
Expected: PASS.

Run: `cd microservices/booking-service && ./mvnw -q test`
Expected: PASS (existing `BackendApplicationTests` unaffected — `Booking`'s new fields are nullable and unused by the legacy flow).

Run: `cd microservices/common-models && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingRequest.java \
        microservices/common-models/src/main/java/com/travel2go/backend/dto/LegBookingResponse.java \
        microservices/common-models/src/main/java/com/travel2go/backend/model/Booking.java \
        microservices/booking-service/src/main/java/com/travel2go/backend/controller/LegBookingController.java \
        microservices/trip-service/src/main/java/com/travel2go/backend/client/BookingClient.java \
        microservices/trip-service/src/main/java/com/travel2go/backend/service/TripService.java \
        microservices/trip-service/src/main/java/com/travel2go/backend/controller/TripController.java \
        microservices/trip-service/src/main/java/com/travel2go/backend/dto/BookLegRequest.java \
        microservices/trip-service/src/test/java/com/travel2go/backend/service/TripServiceTest.java
git commit -m "feat: trip-service orchestrates leg-level bookings via booking-service (P0-5)"
```

---

### Task 6: Clean up — regenerate `tree.txt`, add `OPEN_QUESTIONS.md` and `MIGRATIONS.md`

**Files:**
- Delete: `microservices/api-gateway/tree.txt`, `microservices/booking-service/tree.txt`, `microservices/identity-service/tree.txt`, `microservices/media-service/tree.txt`, `microservices/notification-service/tree.txt`, `microservices/package-service/tree.txt`, `microservices/common-models/tree.txt` (if present)
- Create: `OPEN_QUESTIONS.md`, `MIGRATIONS.md` (repo root)

**Interfaces:** None.

- [ ] **Step 1: Delete the stale `tree.txt` dumps**

The architecture audit found every `tree.txt` under `microservices/*/` contains an identical Maven `dependency:tree` output for `com.travel2go:backend` — a copy-paste artifact, not a real per-service directory listing (confirmed in the earlier repo audit). Delete them rather than regenerate, since nothing consumes them:

```bash
git rm -f microservices/api-gateway/tree.txt \
          microservices/booking-service/tree.txt \
          microservices/identity-service/tree.txt \
          microservices/media-service/tree.txt \
          microservices/notification-service/tree.txt \
          microservices/package-service/tree.txt
git status
```
Expected: none of the six files remain tracked.

- [ ] **Step 2: Create `OPEN_QUESTIONS.md`**

Create `OPEN_QUESTIONS.md`:

```markdown
# Open Questions

External contracts and decisions this build stubbed behind an interface rather than guessing. Resolve before the corresponding phase ships to production.

## Payments (Phase 0, `payment-service`)
- **UPI provider**: Razorpay vs. Cashfree not yet chosen; no API keys exist in this repo. `PaymentProvider` is implemented today by `SandboxUpiProvider`, which always returns `SUCCESS` with a synthetic `providerRef`. Swap in a real implementation behind the same interface once a provider + keys are confirmed.
- **Quote-token secret**: `quote.token.secret` currently ships a placeholder default in `application.properties`. Must be set to a real secret via `QUOTE_TOKEN_SECRET` in the deploy workflow before this reaches production traffic that isn't sandboxed.

## Infrastructure (Phase 0 architecture report, carried forward)
- **Redis / RabbitMQ in production**: `.github/workflows/backend-deploy.yml` injects `SPRING_REDIS_HOST` / `SPRING_RABBITMQ_HOST` from GitHub secrets rather than the docker-compose hostnames, implying managed instances (e.g. Memorystore, CloudAMQP) — the exact provider is not declared anywhere in this repository.
- **`backend/` monolith retirement**: confirmed unreferenced by CI, docker-compose, and the frontend, and deleted in Phase 0 Task 1. If anyone was still hitting it directly (bypassing the gateway), that integration is now broken — watch for reports.

## Phase 1+ (from the PRD, not yet started)
- IRCTC authorised-partner access for `inventory-service`'s `RailAdapter` — available, or does rail launch management-only over public data first?
- WhatsApp Business API BSP (Gupshup / Meta direct / other) and template approval status, for `notification-service`'s WhatsApp channel.
- Firestore's fit for itinerary/monitoring queries at scale (e.g. "all trips with a leg delayed in the next 6h") — needs validation before `journey-monitor-function` (Phase 2) scales.
```

- [ ] **Step 3: Create `MIGRATIONS.md`**

Create `MIGRATIONS.md`:

```markdown
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
```

- [ ] **Step 4: Commit**

```bash
git add OPEN_QUESTIONS.md MIGRATIONS.md
git commit -m "chore: remove stale tree.txt dumps, add OPEN_QUESTIONS.md and MIGRATIONS.md"
```

---

## Phase 0 exit check

Before considering Phase 0 done, confirm every AC from the PRD:

- [ ] Only `microservices/` builds/deploys; `backend/` is gone (Task 1).
- [ ] `Trip`, `Leg`, `Traveller`, `TravellerGroup` compile and are shared via `common-models` (Task 2).
- [ ] A Trip can be created, a leg added, and the full itinerary retrieved; `trip.created` publishes to `trip.exchange` (Task 3).
- [ ] A payment succeeds only when `quoteToken` matches leg + price; `feePaise` is always `0` (Task 4 — enforced by `QuoteTokenServiceTest` and `PaymentServiceTest`).
- [ ] Booking a leg updates the Trip's leg and emits `leg.booked` (Task 5 — enforced by `TripServiceTest`).
- [ ] Repo is tidy; unknowns are logged in `OPEN_QUESTIONS.md` (Task 6).
