# P1.1 Payment Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace payment-service's synchronous always-succeeds `SandboxUpiProvider` with a real async Razorpay-backed order/webhook/refund flow, so a leg is confirmed only after a verified `CAPTURED` webhook — never on order creation or an unverified signal.

**Architecture:** `PaymentProvider` interface grows from one method (`charge`) to three (`createOrder`, `verifyAndParse`, `refund`), swappable via `payment.provider=razorpay|sandbox`. `PaymentService` gains `createOrder`/`applyWebhook`/`getStatus`/`refund`, backed by two new Firestore-side pieces: a `providerPaymentId` field on the shared `Payment` model, and a new `processed_webhook_events` collection for idempotency dedup. On a verified capture, a new `PaymentEventPublisher` (payment-service's first RabbitMQ wiring) emits `payment.captured` onto the existing `trip.exchange`. The webhook path is reachable from Razorpay through a new, narrowly-scoped api-gateway route while the rest of payment-service stays `--no-allow-unauthenticated`.

**Tech Stack:** Spring Boot 3.2.4 / Java 17, `com.razorpay:razorpay-java` (real provider), Spring AMQP (RabbitMQ), Firestore reactive repositories, JUnit 5 + Mockito + AssertJ (existing test stack).

## Global Constraints

- **G1:** `feePaise` is always `0L`. Never changes across this plan.
- **G2:** `QuoteTokenService.isValid(token, bookingRef, amountPaise)` (in `platform-security`, package `com.travel2go.backend.service`) gates order creation exactly as it gated the old `charge()`. Do not modify `QuoteTokenService` itself.
- **A2 fail-fast pattern:** every new secret (`razorpay.key-id`, `razorpay.key-secret`, `razorpay.webhook-secret`) is `${ENV_VAR}` with no in-source default, and the bean that needs it fails fast via `@PostConstruct` (mirror `JwtUtil`/`QuoteTokenService` — reject blank/short values, never log the secret itself).
- **A3 scoping:** new secrets and `SPRING_RABBITMQ_*` reach payment-service only via the existing per-service `case "$SVC" in ...)` blocks in `backend-deploy.yml` — never the "every service gets everything" pattern that A3 removed.
- **Raw-body HMAC:** the webhook signature is verified over the exact bytes the client sent (`@RequestBody byte[]`), never a re-serialized DTO.
- **Always `200` for a verified, understood webhook** (including duplicates and business-rule rejections like amount mismatch); `400` only for signature failure.
- **`Payment.status` stays a `String`** (existing repo convention), allowed values now: `CREATED | CAPTURED | FAILED | REJECTED | REFUNDED`.
- No task in this plan touches P1.2 (booking-service validation), P1.3 (Postgres ledger), or P1.4 (saga).

---

### Task 1: payment-service dependencies

**Files:**
- Modify: `microservices/payment-service/pom.xml`

**Interfaces:**
- Produces: `com.razorpay.RazorpayClient`, `com.razorpay.Utils`, `com.razorpay.RazorpayException`, `com.razorpay.Order`, `com.razorpay.Refund` (from razorpay-java) and `org.springframework.amqp.rabbit.core.RabbitTemplate`, `org.springframework.amqp.core.TopicExchange` (from spring-boot-starter-amqp) available to every later task in this plan.

- [ ] **Step 1: Add the two new dependencies**

In `microservices/payment-service/pom.xml`, inside the existing `<dependencies>` block, add these two entries (placed after the existing `platform-security` dependency, before `lombok`):

```xml
		<dependency>
			<groupId>com.razorpay</groupId>
			<artifactId>razorpay-java</artifactId>
			<version>1.4.4</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-amqp</artifactId>
		</dependency>
```

- [ ] **Step 2: Verify dependencies resolve and the module still compiles**

Run: `cd microservices/payment-service && ./mvnw -q clean compile`
Expected: `BUILD SUCCESS`, no output. If `razorpay-java:1.4.4` fails to resolve, check https://mvnrepository.com/artifact/com.razorpay/razorpay-java for the current latest 1.4.x version and use that instead — this is the assumption flagged in the P1.1 brief §11 to verify.

- [ ] **Step 3: Commit**

```bash
git add microservices/payment-service/pom.xml
git commit -m "P1.1: add razorpay-java and spring-boot-starter-amqp to payment-service"
```

---

### Task 2: Payment model field + PaymentProvider contract types

**Files:**
- Modify: `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/CreatedOrder.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/WebhookEvent.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/WebhookEventType.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/RefundResult.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/InvalidWebhookSignatureException.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/PaymentProvider.java`
- Delete: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/PaymentResult.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `Payment.getProviderPaymentId()/setProviderPaymentId(String)`; `CreatedOrder(String providerOrderId, String keyId, long amountPaise, String currency)`; `WebhookEvent(WebhookEventType type, String providerOrderId, String providerPaymentId, long amountPaise)`; `WebhookEventType.{CAPTURED,FAILED,OTHER}`; `RefundResult(String status, String providerRefundId)`; `InvalidWebhookSignatureException(String message)`; `PaymentProvider.createOrder(String,long,String)`, `.verifyAndParse(byte[], Map<String,String>)`, `.refund(String,long)` — the interface every provider task and `PaymentService` depends on.

This task is pure data-shape/contract — no behavior to unit test. Verify by compiling.

- [ ] **Step 1: Add `providerPaymentId` to the shared Payment model**

In `microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java`, replace the whole file with:

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
    private String status; // CREATED | CAPTURED | FAILED | REJECTED | REFUNDED

    private Long amountPaise;
    private Long feePaise; // MUST always be 0 (G1 - zero booking/convenience fee)

    private String providerRef; // provider ORDER id (set at CREATED)
    private String providerPaymentId; // provider PAYMENT id (set on CAPTURED, from the webhook)
    private Boolean quoteTokenValidated;

    private Date createdAt;
}
```

- [ ] **Step 2: Create the provider contract DTOs**

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/CreatedOrder.java`:

```java
package com.travel2go.backend.provider;

import lombok.Value;

@Value
public class CreatedOrder {
    String providerOrderId;
    String keyId;
    long amountPaise;
    String currency;
}
```

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/WebhookEventType.java`:

```java
package com.travel2go.backend.provider;

public enum WebhookEventType {
    CAPTURED,
    FAILED,
    OTHER
}
```

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/WebhookEvent.java`:

```java
package com.travel2go.backend.provider;

import lombok.Value;

@Value
public class WebhookEvent {
    WebhookEventType type;
    String providerOrderId;
    String providerPaymentId;
    long amountPaise;
}
```

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/RefundResult.java`:

```java
package com.travel2go.backend.provider;

import lombok.Value;

@Value
public class RefundResult {
    String status; // SUCCESS | FAILED
    String providerRefundId;
}
```

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/InvalidWebhookSignatureException.java`:

```java
package com.travel2go.backend.provider;

public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Rewrite the PaymentProvider interface**

Replace `microservices/payment-service/src/main/java/com/travel2go/backend/provider/PaymentProvider.java` with:

```java
package com.travel2go.backend.provider;

import java.util.Map;

public interface PaymentProvider {

    CreatedOrder createOrder(String reference, long amountPaise, String method);

    /**
     * Verifies the raw webhook body's signature and parses it.
     * @throws InvalidWebhookSignatureException if the signature is missing or invalid.
     */
    WebhookEvent verifyAndParse(byte[] rawBody, Map<String, String> headers);

    RefundResult refund(String providerPaymentId, long amountPaise);
}
```

- [ ] **Step 4: Delete the superseded single-shot result type**

```bash
git rm microservices/payment-service/src/main/java/com/travel2go/backend/provider/PaymentResult.java
```

- [ ] **Step 5: Confirm the module still compiles (it will fail — expected, fixed in later tasks)**

Run: `cd microservices/payment-service && ./mvnw -q clean compile`
Expected: **FAIL** — `SandboxUpiProvider` still implements the old single-method `PaymentProvider` and `PaymentService`/`PaymentController` still reference `PaymentResult`/the old `charge()` signature. This is expected; Tasks 3, 6, 7, 8 fix each of these in turn. Do not attempt to fix them here.

- [ ] **Step 6: Commit**

```bash
git add microservices/common-models/src/main/java/com/travel2go/backend/model/Payment.java
git add microservices/payment-service/src/main/java/com/travel2go/backend/provider/
git commit -m "P1.1: add providerPaymentId to Payment, rewrite PaymentProvider contract to createOrder/verifyAndParse/refund"
```

---

### Task 3: Shared webhook payload parser + SandboxPaymentProvider

**Files:**
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/WebhookPayloadParser.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/SandboxPaymentProvider.java`
- Delete: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/SandboxUpiProvider.java`
- Test: `microservices/payment-service/src/test/java/com/travel2go/backend/provider/SandboxPaymentProviderTest.java`

**Interfaces:**
- Consumes: `PaymentProvider`, `CreatedOrder`, `WebhookEvent`, `WebhookEventType`, `RefundResult`, `InvalidWebhookSignatureException` (Task 2).
- Produces: `SandboxPaymentProvider.sign(byte[] rawBody) -> String` (test/local helper, also usable by Task 8's controller test); the `payment.provider=sandbox` bean, active only when that property is set (never in prod default).

Both Sandbox and Razorpay parse the identical assumed Razorpay webhook JSON shape:
```json
{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_x","order_id":"order_x","amount":150000}}}}
```
with the signature in an `X-Razorpay-Signature` header. **This shape and header name are the P1.1 brief §11 assumption to verify against current Razorpay docs before going live.**

- [ ] **Step 1: Write the shared payload parser (no test — pure parsing, exercised transitively by the tests below)**

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/WebhookPayloadParser.java`:

```java
package com.travel2go.backend.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Shared by SandboxPaymentProvider and RazorpayProvider so both parse the
 * assumed Razorpay webhook JSON shape identically - only signature
 * verification differs between them.
 */
final class WebhookPayloadParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebhookPayloadParser() {
    }

    static WebhookEvent parse(byte[] rawBody) {
        JsonNode root;
        try {
            root = MAPPER.readTree(rawBody);
        } catch (IOException e) {
            throw new UncheckedIOException("Malformed webhook payload", e);
        }

        String event = root.path("event").asText("");
        JsonNode entity = root.path("payload").path("payment").path("entity");

        WebhookEventType type;
        if ("payment.captured".equals(event)) {
            type = WebhookEventType.CAPTURED;
        } else if ("payment.failed".equals(event)) {
            type = WebhookEventType.FAILED;
        } else {
            type = WebhookEventType.OTHER;
        }

        return new WebhookEvent(
                type,
                entity.path("order_id").asText(null),
                entity.path("id").asText(null),
                entity.path("amount").asLong(0L));
    }
}
```

- [ ] **Step 2: Write the failing test for SandboxPaymentProvider**

`microservices/payment-service/src/test/java/com/travel2go/backend/provider/SandboxPaymentProviderTest.java`:

```java
package com.travel2go.backend.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPaymentProviderTest {

    private SandboxPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SandboxPaymentProvider();
        ReflectionTestUtils.setField(provider, "webhookSecret", "test-sandbox-webhook-secret-1234567890");
    }

    @Test
    void createOrder_returnsDeterministicSandboxOrder() {
        CreatedOrder order = provider.createOrder("leg-1", 150000L, "UPI");

        assertThat(order.getProviderOrderId()).startsWith("sandbox_order_");
        assertThat(order.getAmountPaise()).isEqualTo(150000L);
        assertThat(order.getCurrency()).isEqualTo("INR");
    }

    @Test
    void verifyAndParse_acceptsCorrectlySignedCapturedPayload() {
        byte[] payload = ("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":150000}}}}")
                .getBytes(StandardCharsets.UTF_8);
        String signature = provider.sign(payload);

        WebhookEvent event = provider.verifyAndParse(payload, Map.of("X-Razorpay-Signature", signature));

        assertThat(event.getType()).isEqualTo(WebhookEventType.CAPTURED);
        assertThat(event.getProviderOrderId()).isEqualTo("order_1");
        assertThat(event.getProviderPaymentId()).isEqualTo("pay_1");
        assertThat(event.getAmountPaise()).isEqualTo(150000L);
    }

    @Test
    void verifyAndParse_rejectsTamperedSignature() {
        byte[] payload = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String badSignature = provider.sign(payload) + "tampered";

        assertThatThrownBy(() -> provider.verifyAndParse(payload, Map.of("X-Razorpay-Signature", badSignature)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verifyAndParse_rejectsMissingSignatureHeader() {
        byte[] payload = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider.verifyAndParse(payload, Map.of()))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void refund_returnsDeterministicSuccess() {
        RefundResult result = provider.refund("pay_1", 150000L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getProviderRefundId()).startsWith("sandbox_refund_");
    }
}
```

- [ ] **Step 3: Run the test to confirm it fails**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=SandboxPaymentProviderTest`
Expected: FAIL — `SandboxPaymentProvider` does not exist yet (compile error).

- [ ] **Step 4: Implement SandboxPaymentProvider**

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/SandboxPaymentProvider.java`:

```java
package com.travel2go.backend.provider;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic PaymentProvider for local/test use. Performs a real
 * HMAC-SHA256 signature check against a local secret so the
 * signature-rejection branch is exercised the same way RazorpayProvider
 * exercises it, without needing live Razorpay credentials.
 *
 * Active when payment.provider=sandbox (see application-local.properties).
 * Never active in the default (prod) profile - RazorpayProvider is.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "sandbox")
public class SandboxPaymentProvider implements PaymentProvider {

    private static final String HMAC_ALGO = "HmacSHA256";
    static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    void validateSecret() {
        int len = webhookSecret == null ? 0 : webhookSecret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "razorpay.webhook-secret must be set and at least 32 bytes (was " + len + "). Refusing to start.");
        }
    }

    @Override
    public CreatedOrder createOrder(String reference, long amountPaise, String method) {
        return new CreatedOrder("sandbox_order_" + UUID.randomUUID(), "sandbox_key_id", amountPaise, "INR");
    }

    @Override
    public WebhookEvent verifyAndParse(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(SIGNATURE_HEADER);
        if (signature == null || !sign(rawBody).equals(signature)) {
            throw new InvalidWebhookSignatureException("sandbox webhook signature mismatch");
        }
        return WebhookPayloadParser.parse(rawBody);
    }

    @Override
    public RefundResult refund(String providerPaymentId, long amountPaise) {
        return new RefundResult("SUCCESS", "sandbox_refund_" + UUID.randomUUID());
    }

    /** Test/local helper: signs a payload the same way verifyAndParse expects it signed. */
    public String sign(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(rawBody);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign sandbox webhook payload", e);
        }
    }
}
```

- [ ] **Step 5: Delete the old sandbox provider**

```bash
git rm microservices/payment-service/src/main/java/com/travel2go/backend/provider/SandboxUpiProvider.java
```

- [ ] **Step 6: Run the test to confirm it passes**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=SandboxPaymentProviderTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add microservices/payment-service/src/main/java/com/travel2go/backend/provider/
git add microservices/payment-service/src/test/java/com/travel2go/backend/provider/
git commit -m "P1.1: add WebhookPayloadParser + SandboxPaymentProvider, remove SandboxUpiProvider"
```

---

### Task 4: Webhook idempotency store

**Files:**
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/webhook/ProcessedWebhookEvent.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/webhook/ProcessedWebhookEventRepository.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/PaymentServiceApplicationTests.java`

**Interfaces:**
- Produces: `ProcessedWebhookEvent{id, processedAt}` (Firestore doc, `id` = provider payment id), `ProcessedWebhookEventRepository extends FirestoreReactiveRepository<ProcessedWebhookEvent>` with inherited `Mono<ProcessedWebhookEvent> findById(String)` and `Mono<ProcessedWebhookEvent> save(ProcessedWebhookEvent)` — used by `PaymentService` in Task 7.

Pure data-shape task, no behavior to unit test beyond compilation; the context-load test in Step 3 is the verification.

- [ ] **Step 1: Create the Firestore model**

`microservices/payment-service/src/main/java/com/travel2go/backend/webhook/ProcessedWebhookEvent.java`:

```java
package com.travel2go.backend.webhook;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Idempotency record: one document per provider payment id already captured,
 * so a re-delivered "payment.captured" webhook is a no-op instead of a
 * double-capture / double-publish.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "processed_webhook_events")
public class ProcessedWebhookEvent {
    @DocumentId
    private String id; // the provider payment id

    private Date processedAt;
}
```

- [ ] **Step 2: Create the repository**

`microservices/payment-service/src/main/java/com/travel2go/backend/webhook/ProcessedWebhookEventRepository.java`:

```java
package com.travel2go.backend.webhook;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWebhookEventRepository extends FirestoreReactiveRepository<ProcessedWebhookEvent> {
}
```

- [ ] **Step 3: Mock the new repository in the context-load test**

In `microservices/payment-service/src/test/java/com/travel2go/backend/PaymentServiceApplicationTests.java`, the existing `@MockBean({PaymentRepository.class})` must also mock the new repository (a real one would try to hit Firestore during context load, same reason `PaymentRepository` is already mocked there). Replace the file with:

```java
package com.travel2go.backend;

import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
    "spring.cloud.gcp.firestore.enabled=false",
    "spring.cloud.gcp.storage.enabled=false",
    "spring.cloud.gcp.core.enabled=false"
})
@MockBean({PaymentRepository.class, ProcessedWebhookEventRepository.class})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
```

- [ ] **Step 4: Verify compile (context-load test will still fail until Task 8 finishes the controller/service wiring — that's expected)**

Run: `cd microservices/payment-service && ./mvnw -q clean compile`
Expected: `BUILD SUCCESS` for `compile` (this task only adds new files; nothing existing references them yet, so compile succeeds even though `test` would still fail from Task 2's known-broken state).

- [ ] **Step 5: Commit**

```bash
git add microservices/payment-service/src/main/java/com/travel2go/backend/webhook/
git add microservices/payment-service/src/test/java/com/travel2go/backend/PaymentServiceApplicationTests.java
git commit -m "P1.1: add processed_webhook_events Firestore collection for webhook idempotency"
```

---

### Task 5: RabbitMQ wiring for payment-service

**Files:**
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentEventPublisher.java`

**Interfaces:**
- Consumes: `org.springframework.amqp.rabbit.core.RabbitTemplate` (autoconfigured by `spring-boot-starter-amqp`, Task 1).
- Produces: `PaymentEventPublisher.publish(String routingKey, Object payload)` — used by `PaymentService` in Task 7 to emit `payment.captured`/`payment.refunded`.

This is a 1:1 mirror of trip-service's already-untested `TripEventPublisher` (`microservices/trip-service/src/main/java/com/travel2go/backend/service/TripEventPublisher.java`) — no dedicated test exists for that class in the codebase either, so none is added here (thin `RabbitTemplate` delegation, YAGNI). Verified indirectly through `PaymentServiceTest`'s `verify(eventPublisher).publish(...)` mocks in Task 7.

- [ ] **Step 1: Declare the shared exchange**

`microservices/payment-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java`:

```java
package com.travel2go.backend.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the same trip.exchange TopicExchange that trip-service declares.
 * RabbitMQ exchange declaration is idempotent for an identical name+type, so
 * both services declaring it is safe regardless of startup order.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }
}
```

- [ ] **Step 2: Add the publisher**

`microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentEventPublisher.java`:

```java
package com.travel2go.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    public static final String EXCHANGE = "trip.exchange";

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `cd microservices/payment-service && ./mvnw -q clean compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add microservices/payment-service/src/main/java/com/travel2go/backend/config/RabbitMQConfig.java
git add microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentEventPublisher.java
git commit -m "P1.1: wire RabbitMQ into payment-service (trip.exchange, PaymentEventPublisher)"
```

---

### Task 6: RazorpayProvider

**Files:**
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/provider/RazorpayProvider.java`

**Interfaces:**
- Consumes: `PaymentProvider`, `CreatedOrder`, `WebhookEvent`, `RefundResult`, `InvalidWebhookSignatureException`, `WebhookPayloadParser.parse(byte[])` (Tasks 2-3); `com.razorpay.*` (Task 1).
- Produces: the `payment.provider=razorpay` (default) `PaymentProvider` bean — the one active in production.

No dedicated unit test: it is a thin wrapper around the Razorpay SDK with no branching logic of its own beyond what `WebhookPayloadParser`/the SDK already own, and cannot be exercised without live Razorpay credentials or a mock HTTP server (out of scope for this brief — `SandboxPaymentProvider`, already tested in Task 3, is the swappable stand-in used everywhere else). Verify by compilation and a manual smoke read of the assumptions below.

- [ ] **Step 1: Implement RazorpayProvider**

`microservices/payment-service/src/main/java/com/travel2go/backend/provider/RazorpayProvider.java`:

```java
package com.travel2go.backend.provider;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Real Razorpay integration - active by default (payment.provider unset or
 * =razorpay). Assumptions made against razorpay-java 1.4.x (verify against
 * current Razorpay docs before going live - see hand-back notes in
 * docs/superpowers/plans/2026-08-31-p1-1-payment-provider.md Task 6):
 *   - Orders API: client.orders.create(JSONObject) returns an Order whose
 *     "id" field is the provider order id.
 *   - Webhook signature: com.razorpay.Utils.verifyWebhookSignature(payload,
 *     signature, secret) throws RazorpayException on mismatch.
 *   - Refunds API: client.payments.refund(paymentId, JSONObject) returns a
 *     Refund whose "id" field is the provider refund id.
 *   - Webhook payload shape: { "event": "payment.captured"|"payment.failed",
 *     "payload": { "payment": { "entity": { "id", "order_id", "amount" } } } },
 *     signature carried in the "X-Razorpay-Signature" header - same shape
 *     WebhookPayloadParser and SandboxPaymentProvider already assume.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "razorpay", matchIfMissing = true)
public class RazorpayProvider implements PaymentProvider {

    static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    private RazorpayClient client;

    @PostConstruct
    void init() {
        requireNonBlank(keyId, "razorpay.key-id");
        requireNonBlank(keySecret, "razorpay.key-secret");
        requireNonBlank(webhookSecret, "razorpay.webhook-secret");
        try {
            client = new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new IllegalStateException("Failed to initialize Razorpay client", e);
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set. Refusing to start.");
        }
    }

    @Override
    public CreatedOrder createOrder(String reference, long amountPaise, String method) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", "INR");
            request.put("receipt", reference);
            request.put("payment_capture", 1);
            Order order = client.orders.create(request);
            return new CreatedOrder(order.get("id"), keyId, amountPaise, "INR");
        } catch (RazorpayException e) {
            throw new IllegalStateException("Razorpay order creation failed for " + reference, e);
        }
    }

    @Override
    public WebhookEvent verifyAndParse(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(SIGNATURE_HEADER);
        if (signature == null) {
            throw new InvalidWebhookSignatureException("missing " + SIGNATURE_HEADER + " header");
        }
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        try {
            Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            throw new InvalidWebhookSignatureException("razorpay webhook signature invalid: " + e.getMessage());
        }
        return WebhookPayloadParser.parse(rawBody);
    }

    @Override
    public RefundResult refund(String providerPaymentId, long amountPaise) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            Refund refund = client.payments.refund(providerPaymentId, request);
            return new RefundResult("SUCCESS", refund.get("id"));
        } catch (RazorpayException e) {
            return new RefundResult("FAILED", null);
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `cd microservices/payment-service && ./mvnw -q clean compile`
Expected: `BUILD SUCCESS`. If `org.json.JSONObject` fails to resolve, add `org.json:json` explicitly as a dependency in `pom.xml` (razorpay-java should bring it transitively, but pin an explicit version, e.g. `20240303`, if Maven can't resolve it transitively) — note this as a second Task 6 assumption to flag in hand-back.

- [ ] **Step 3: Commit**

```bash
git add microservices/payment-service/src/main/java/com/travel2go/backend/provider/RazorpayProvider.java
git commit -m "P1.1: add RazorpayProvider (real Razorpay order/webhook/refund integration)"
```

---

### Task 7: PaymentService rewrite

**Files:**
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentCapturedEvent.java`
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentRefundedEvent.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java`
- Modify: `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java`

**Interfaces:**
- Consumes: `PaymentProvider`, `CreatedOrder`, `WebhookEvent`, `WebhookEventType`, `RefundResult` (Tasks 2-3, 6); `ProcessedWebhookEventRepository`, `ProcessedWebhookEvent` (Task 4); `PaymentEventPublisher` (Task 5); `QuoteTokenService.isValid(String,String,long)` (existing, `platform-security`, same package `com.travel2go.backend.service` — no import needed).
- Produces: `PaymentService.createOrder(String bookingRef, long amountPaise, String method, String quoteToken) -> Payment`, `.applyWebhook(byte[] rawBody, Map<String,String> headers) -> void` (throws `InvalidWebhookSignatureException` on bad signature — caller maps to 400), `.getStatus(String bookingRef) -> Payment`, `.refund(String bookingRef) -> Payment` — all consumed by `PaymentController` in Task 8.

- [ ] **Step 1: Add the two event payload types**

`microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentCapturedEvent.java`:

```java
package com.travel2go.backend.service;

public record PaymentCapturedEvent(String bookingRef, String providerPaymentId, long amountPaise) {
}
```

`microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentRefundedEvent.java`:

```java
package com.travel2go.backend.service;

public record PaymentRefundedEvent(String bookingRef, String providerRefundId, long amountPaise) {
}
```

- [ ] **Step 2: Add the two lookup methods PaymentService needs**

Replace `microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java` with:

```java
package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.Payment;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PaymentRepository extends FirestoreReactiveRepository<Payment> {
    Flux<Payment> findByBookingRef(String bookingRef);

    Flux<Payment> findByProviderRef(String providerRef);
}
```

- [ ] **Step 3: Write the failing tests for PaymentService**

Replace `microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java` with:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEvent;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;
    @Mock private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, processedWebhookEventRepository, paymentProvider, quoteTokenService, eventPublisher);
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private Payment createdPayment() {
        return Payment.builder()
                .id("p1")
                .bookingRef("leg-1")
                .method("UPI")
                .status("CREATED")
                .amountPaise(150000L)
                .feePaise(0L)
                .providerRef("order_1")
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();
    }

    @Test
    void createOrder_succeedsAndNeverAddsAFee() {
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_1", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token");

        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        assertThat(result.getProviderRef()).isEqualTo("order_1");
    }

    @Test
    void createOrder_rejectsWhenQuoteTokenInvalid_withoutCallingProvider() {
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "bad-token");

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
    }

    @Test
    void applyWebhook_capturedTransitionsPaymentAndPublishesEvent() {
        Payment payment = createdPayment();
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(payment));
        when(processedWebhookEventRepository.findById("pay_1")).thenReturn(Mono.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository).save(argThat(p -> "CAPTURED".equals(p.getStatus()) && "pay_1".equals(p.getProviderPaymentId())));
        verify(eventPublisher).publish(eq("payment.captured"),
                eq(new PaymentCapturedEvent("leg-1", "pay_1", 150000L)));
    }

    @Test
    void applyWebhook_duplicateCapturedIsIdempotent_publishesEventOnlyOnce() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(createdPayment()));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.findById("pay_1"))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(ProcessedWebhookEvent.builder().id("pay_1").processedAt(new Date()).build()));
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        paymentService.applyWebhook("{}".getBytes(), Map.of());
        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }

    @Test
    void applyWebhook_amountMismatchDoesNotCapture() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(createdPayment()));
        when(processedWebhookEventRepository.findById("pay_1")).thenReturn(Mono.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 999L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void applyWebhook_failedAfterCapturedIsIgnored() {
        Payment alreadyCaptured = createdPayment();
        alreadyCaptured.setStatus("CAPTURED");
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(alreadyCaptured));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.FAILED, "order_1", "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void applyWebhook_unknownOrderIsIgnored() {
        when(paymentRepository.findByProviderRef("order_unknown")).thenReturn(Flux.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_unknown", "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refund_transitionsCapturedToRefunded() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(captured));
        when(paymentProvider.refund("pay_1", 150000L)).thenReturn(new RefundResult("SUCCESS", "rfnd_1"));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(eventPublisher).publish(eq("payment.refunded"),
                eq(new PaymentRefundedEvent("leg-1", "rfnd_1", 150000L)));
    }

    @Test
    void refund_isIdempotentOnAlreadyRefunded() {
        Payment refunded = createdPayment();
        refunded.setStatus("REFUNDED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(refunded));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(paymentProvider, never()).refund(any(), anyLong());
        verify(eventPublisher, never()).publish(eq("payment.refunded"), any());
    }
}
```

- [ ] **Step 4: Run tests to confirm they fail**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentServiceTest`
Expected: FAIL — `PaymentService`'s constructor doesn't match yet (still the old 3-arg `charge()`-based version).

- [ ] **Step 5: Rewrite PaymentService**

Replace `microservices/payment-service/src/main/java/com/travel2go/backend/service/PaymentService.java` with:

```java
package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEvent;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final PaymentProvider paymentProvider;
    private final QuoteTokenService quoteTokenService;
    private final PaymentEventPublisher eventPublisher;

    public Payment createOrder(String bookingRef, long amountPaise, String method, String quoteToken) {
        boolean quoteValid = quoteTokenService.isValid(quoteToken, bookingRef, amountPaise);

        if (!quoteValid) {
            Payment rejected = Payment.builder()
                    .bookingRef(bookingRef)
                    .method(method)
                    .status("REJECTED")
                    .amountPaise(amountPaise)
                    .feePaise(0L)
                    .quoteTokenValidated(false)
                    .createdAt(new Date())
                    .build();
            return paymentRepository.save(rejected).block();
        }

        CreatedOrder order = paymentProvider.createOrder(bookingRef, amountPaise, method);

        Payment payment = Payment.builder()
                .bookingRef(bookingRef)
                .method(method)
                .status("CREATED")
                .amountPaise(amountPaise)
                .feePaise(0L)
                .providerRef(order.getProviderOrderId())
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();

        return paymentRepository.save(payment).block();
    }

    public void applyWebhook(byte[] rawBody, Map<String, String> headers) {
        WebhookEvent event = paymentProvider.verifyAndParse(rawBody, headers);

        if (event.getType() == WebhookEventType.OTHER) {
            return;
        }

        String dedupeKey = event.getProviderPaymentId();
        if (dedupeKey != null && processedWebhookEventRepository.findById(dedupeKey).block() != null) {
            log.info("Webhook for payment {} already processed, skipping", dedupeKey);
            return;
        }

        Payment payment = paymentRepository.findByProviderRef(event.getProviderOrderId())
                .collectList()
                .map(list -> list.stream().findFirst().orElse(null))
                .block();

        if (payment == null) {
            log.warn("Webhook for unknown provider order {}", event.getProviderOrderId());
            return;
        }

        if (!"CREATED".equals(payment.getStatus())) {
            log.info("Ignoring webhook for order {} - payment already in terminal status {}",
                    event.getProviderOrderId(), payment.getStatus());
            return;
        }

        if (event.getType() == WebhookEventType.CAPTURED) {
            if (payment.getAmountPaise() != event.getAmountPaise()) {
                log.error("Amount mismatch for order {}: expected {} got {} - not capturing",
                        event.getProviderOrderId(), payment.getAmountPaise(), event.getAmountPaise());
                return;
            }
            payment.setStatus("CAPTURED");
            payment.setProviderPaymentId(event.getProviderPaymentId());
            paymentRepository.save(payment).block();

            if (dedupeKey != null) {
                processedWebhookEventRepository.save(
                        ProcessedWebhookEvent.builder().id(dedupeKey).processedAt(new Date()).build()).block();
            }

            eventPublisher.publish("payment.captured",
                    new PaymentCapturedEvent(payment.getBookingRef(), payment.getProviderPaymentId(), payment.getAmountPaise()));
        } else {
            payment.setStatus("FAILED");
            paymentRepository.save(payment).block();
        }
    }

    public Payment getStatus(String bookingRef) {
        return paymentRepository.findByBookingRef(bookingRef)
                .collectList()
                .map(list -> list.stream()
                        .max(Comparator.comparing(Payment::getCreatedAt))
                        .orElseThrow(() -> new IllegalArgumentException("No payment found for bookingRef " + bookingRef)))
                .block();
    }

    public Payment refund(String bookingRef) {
        Payment payment = getStatus(bookingRef);

        if ("REFUNDED".equals(payment.getStatus())) {
            return payment;
        }
        if (!"CAPTURED".equals(payment.getStatus())) {
            throw new IllegalStateException("Cannot refund payment in status " + payment.getStatus());
        }

        RefundResult result = paymentProvider.refund(payment.getProviderPaymentId(), payment.getAmountPaise());
        if (!"SUCCESS".equals(result.getStatus())) {
            throw new IllegalStateException("Refund failed for bookingRef " + bookingRef);
        }

        payment.setStatus("REFUNDED");
        Payment saved = paymentRepository.save(payment).block();

        eventPublisher.publish("payment.refunded",
                new PaymentRefundedEvent(bookingRef, result.getProviderRefundId(), payment.getAmountPaise()));

        return saved;
    }
}
```

- [ ] **Step 6: Run tests to confirm they pass**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentServiceTest`
Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add microservices/payment-service/src/main/java/com/travel2go/backend/service/
git add microservices/payment-service/src/main/java/com/travel2go/backend/repository/PaymentRepository.java
git add microservices/payment-service/src/test/java/com/travel2go/backend/service/PaymentServiceTest.java
git commit -m "P1.1: rewrite PaymentService for order/webhook/refund flow with idempotent capture"
```

---

### Task 8: PaymentController, DTOs, SecurityConfig

**Files:**
- Create: `microservices/payment-service/src/main/java/com/travel2go/backend/dto/CreateOrderRequest.java`
- Delete: `microservices/payment-service/src/main/java/com/travel2go/backend/dto/ChargeRequest.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java`
- Modify: `microservices/payment-service/src/main/java/com/travel2go/backend/security/SecurityConfig.java`
- Test: `microservices/payment-service/src/test/java/com/travel2go/backend/controller/PaymentControllerWebhookTest.java`

**Interfaces:**
- Consumes: `PaymentService.createOrder/applyWebhook/getStatus/refund` (Task 7); `InvalidWebhookSignatureException` (Task 2).
- Produces: `POST /api/payments/order`, `POST /api/payments/webhook`, `GET /api/payments/{bookingRef}`, `POST /api/payments/{bookingRef}/refund` — the full public HTTP surface of payment-service.

- [ ] **Step 1: Add the order-creation DTO, remove the old charge DTO**

`microservices/payment-service/src/main/java/com/travel2go/backend/dto/CreateOrderRequest.java`:

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
public class CreateOrderRequest {
    private String bookingRef;
    private Long amountPaise;
    private String method;
    private String quoteToken;
}
```

```bash
git rm microservices/payment-service/src/main/java/com/travel2go/backend/dto/ChargeRequest.java
```

- [ ] **Step 2: Write the failing webhook raw-body test**

This is the one controller-level test worth adding beyond the existing service-unit-test convention (this service has no other `@WebMvcTest`), because raw-body capture is exactly the correctness risk the brief calls out — a re-serialized body silently breaks HMAC verification, and only a real MVC round-trip would catch that class of bug.

`microservices/payment-service/src/test/java/com/travel2go/backend/controller/PaymentControllerWebhookTest.java`:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.provider.InvalidWebhookSignatureException;
import com.travel2go.backend.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@org.springframework.context.annotation.Import(com.travel2go.backend.security.SecurityConfig.class)
class PaymentControllerWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    void webhook_passesExactRawBodyBytesToService() throws Exception {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes();

        mockMvc.perform(post("/api/payments/webhook")
                        .content(body)
                        .header("X-Razorpay-Signature", "sig-123"))
                .andExpect(status().isOk());

        verify(paymentService).applyWebhook(eq(body), any());
    }

    @Test
    void webhook_invalidSignatureReturns400() throws Exception {
        doThrow(new InvalidWebhookSignatureException("bad sig"))
                .when(paymentService).applyWebhook(any(), any());

        mockMvc.perform(post("/api/payments/webhook")
                        .content("{}".getBytes())
                        .header("X-Razorpay-Signature", "wrong"))
                .andExpect(status().isBadRequest());
    }
}
```

Note: `@WebMvcTest` needs `JwtUtil` on the context because `SecurityConfig` `@RequiredArgsConstructor`-injects it; since `platform-security`'s `JwtUtil` is a real `@Component`, and this test only exercises `permitAll()` paths, no mock is required for it to load — if the build reports a missing `JwtUtil` bean when you run this test, add `@MockBean private com.travel2go.backend.security.JwtUtil jwtUtil;` to the test class.

- [ ] **Step 3: Run the test to confirm it fails**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentControllerWebhookTest`
Expected: FAIL — `PaymentController` doesn't have a `/webhook` endpoint yet, and `PaymentService.applyWebhook` doesn't exist on the still-old controller wiring.

- [ ] **Step 4: Rewrite PaymentController**

Replace `microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java` with:

```java
package com.travel2go.backend.controller;

import com.travel2go.backend.dto.CreateOrderRequest;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.InvalidWebhookSignatureException;
import com.travel2go.backend.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/order")
    public ResponseEntity<Payment> createOrder(@RequestBody CreateOrderRequest request) {
        Payment payment = paymentService.createOrder(
                request.getBookingRef(), request.getAmountPaise(), request.getMethod(), request.getQuoteToken());

        if ("REJECTED".equals(payment.getStatus())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(payment);
        }
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] rawBody, HttpServletRequest request) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        try {
            paymentService.applyWebhook(rawBody, headers);
            return ResponseEntity.ok().build();
        } catch (InvalidWebhookSignatureException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{bookingRef}")
    public ResponseEntity<Payment> getStatus(@PathVariable String bookingRef) {
        return ResponseEntity.ok(paymentService.getStatus(bookingRef));
    }

    @PostMapping("/{bookingRef}/refund")
    public ResponseEntity<Payment> refund(@PathVariable String bookingRef) {
        return ResponseEntity.ok(paymentService.refund(bookingRef));
    }
}
```

- [ ] **Step 5: Update SecurityConfig — permitAll webhook, ROLE_ADMIN refund**

In `microservices/payment-service/src/main/java/com/travel2go/backend/security/SecurityConfig.java`, replace the `.authorizeHttpRequests(...)` block:

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/refund").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated()
                )
```

- [ ] **Step 6: Run the test to confirm it passes**

Run: `cd microservices/payment-service && ./mvnw -q test -Dtest=PaymentControllerWebhookTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: Run the full payment-service test suite**

Run: `cd microservices/payment-service && ./mvnw -q clean test`
Expected: `BUILD SUCCESS`, all tests pass (`PaymentServiceApplicationTests`, `SandboxPaymentProviderTest`, `PaymentServiceTest`, `PaymentControllerWebhookTest`).

- [ ] **Step 8: Commit**

```bash
git add microservices/payment-service/src/main/java/com/travel2go/backend/dto/
git add microservices/payment-service/src/main/java/com/travel2go/backend/controller/PaymentController.java
git add microservices/payment-service/src/main/java/com/travel2go/backend/security/SecurityConfig.java
git add microservices/payment-service/src/test/java/com/travel2go/backend/controller/
git commit -m "P1.1: rewrite PaymentController for order/webhook/status/refund, permitAll webhook, ROLE_ADMIN refund"
```

---

### Task 9: payment-service secrets, provider selection, local profile

**Files:**
- Modify: `microservices/payment-service/src/main/resources/application.properties`
- Create: `microservices/payment-service/src/main/resources/application-local.properties`

**Interfaces:**
- Produces: `razorpay.key-id`, `razorpay.key-secret`, `razorpay.webhook-secret`, `payment.provider` properties consumed by `RazorpayProvider`/`SandboxPaymentProvider` (Tasks 3, 6); `spring.rabbitmq.*` properties consumed by Spring AMQP autoconfiguration (Task 5).

- [ ] **Step 1: Add secrets, provider selection, and RabbitMQ config to application.properties**

Append to `microservices/payment-service/src/main/resources/application.properties`:

```properties

payment.provider=razorpay

razorpay.key-id=${RAZORPAY_KEY_ID}
razorpay.key-secret=${RAZORPAY_KEY_SECRET}
razorpay.webhook-secret=${RAZORPAY_WEBHOOK_SECRET}

spring.rabbitmq.host=${SPRING_RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${SPRING_RABBITMQ_PORT:5672}
spring.rabbitmq.username=${SPRING_RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${SPRING_RABBITMQ_PASSWORD:guest}
spring.rabbitmq.virtual-host=${SPRING_RABBITMQ_VIRTUAL_HOST:/}
spring.rabbitmq.ssl.enabled=${SPRING_RABBITMQ_SSL_ENABLED:false}
```

- [ ] **Step 2: Add the local-dev profile**

`microservices/payment-service/src/main/resources/application-local.properties`:

```properties
# P1.1 local-dev profile: run with -Dspring.profiles.active=local
payment.provider=sandbox
razorpay.webhook-secret=local-dev-razorpay-webhook-secret-0123456789
```

- [ ] **Step 3: Verify the full suite still passes with these properties present**

Run: `cd microservices/payment-service && ./mvnw -q clean test`
Expected: `BUILD SUCCESS` (unit tests don't load `application.properties` — this step just guards against a typo breaking the properties file itself; `PaymentServiceApplicationTests`, which does load it, must still pass).

- [ ] **Step 4: Commit**

```bash
git add microservices/payment-service/src/main/resources/application.properties
git add microservices/payment-service/src/main/resources/application-local.properties
git commit -m "P1.1: add RAZORPAY_* secrets (fail-fast, no defaults) and sandbox local profile"
```

---

### Task 10: api-gateway webhook route

**Files:**
- Modify: `microservices/api-gateway/src/main/resources/application.properties`
- Modify: `microservices/api-gateway/src/main/java/com/travel2go/apigateway/config/DownstreamUrlValidator.java`

**Interfaces:**
- Consumes: `PAYMENT_SERVICE_URL` env var (set by Task 11).
- Produces: gateway route `payment-webhook` forwarding `/api/payments/webhook` to payment-service; `DownstreamUrlValidator` now also fails gateway startup if `PAYMENT_SERVICE_URL` is missing/malformed.

- [ ] **Step 1: Replace the "not routed" comment with the new route**

In `microservices/api-gateway/src/main/resources/application.properties`, replace these lines (currently at 47-50):

```properties
# payment-service is intentionally NOT routed through the gateway: it has no
# way to verify a caller owns the bookingRef being charged (see OPEN_QUESTIONS.md,
# "Security follow-ups"). It's internal-only until trip-service calls it directly
# (service-to-service) once refund/checkout orchestration is wired in a later phase.
```

with:

```properties
spring.cloud.gateway.routes[9].id=payment-webhook
spring.cloud.gateway.routes[9].uri=${PAYMENT_SERVICE_URL}
spring.cloud.gateway.routes[9].predicates[0]=Path=/api/payments/webhook

# payment-service is otherwise internal-only (--no-allow-unauthenticated, see
# A4 / infra/terraform/run-invoker.tf); only the webhook path above is fronted
# by the gateway so Razorpay's callback (which carries no GCP identity) can
# reach it. Order/status/refund calls are not gateway-routed yet - they go
# through trip-service service-to-service once that orchestration lands (P1.2+).
```

- [ ] **Step 2: Add PAYMENT_SERVICE_URL to DownstreamUrlValidator**

Replace `microservices/api-gateway/src/main/java/com/travel2go/apigateway/config/DownstreamUrlValidator.java` with:

```java
package com.travel2go.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B6: fail fast if any route target URL is missing/malformed, instead of the
 * gateway silently routing to a localhost default and returning errors at
 * request time. The gateway routes to identity, package, booking, media,
 * trip, and (P1.1) the payment-service webhook path.
 */
@Component
public class DownstreamUrlValidator implements ApplicationRunner {

    private static final String SERVICE = "api-gateway";

    private final Map<String, String> requiredUrls = new LinkedHashMap<>();

    public DownstreamUrlValidator(
            @Value("${IDENTITY_SERVICE_URL:}") String identityUrl,
            @Value("${PACKAGE_SERVICE_URL:}") String packageUrl,
            @Value("${BOOKING_SERVICE_URL:}") String bookingUrl,
            @Value("${MEDIA_SERVICE_URL:}") String mediaUrl,
            @Value("${TRIP_SERVICE_URL:}") String tripUrl,
            @Value("${PAYMENT_SERVICE_URL:}") String paymentUrl) {
        requiredUrls.put("IDENTITY_SERVICE_URL", identityUrl);
        requiredUrls.put("PACKAGE_SERVICE_URL", packageUrl);
        requiredUrls.put("BOOKING_SERVICE_URL", bookingUrl);
        requiredUrls.put("MEDIA_SERVICE_URL", mediaUrl);
        requiredUrls.put("TRIP_SERVICE_URL", tripUrl);
        requiredUrls.put("PAYMENT_SERVICE_URL", paymentUrl);
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();
        requiredUrls.forEach((name, value) -> {
            if (value == null || value.isBlank()) {
                problems.add(name + " is blank");
                return;
            }
            try {
                URI u = URI.create(value.trim());
                boolean okScheme = "http".equals(u.getScheme()) || "https".equals(u.getScheme());
                if (!okScheme || u.getHost() == null) {
                    problems.add(name + "=" + value + " is not a valid http(s) URL");
                }
            } catch (IllegalArgumentException e) {
                problems.add(name + "=" + value + " is not a parseable URL");
            }
        });
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    SERVICE + " refusing to start - invalid route URL config: "
                            + String.join("; ", problems));
        }
    }
}
```

- [ ] **Step 3: Verify api-gateway compiles**

Run: `cd microservices/api-gateway && ./mvnw -q clean compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add microservices/api-gateway/src/main/resources/application.properties
git add microservices/api-gateway/src/main/java/com/travel2go/apigateway/config/DownstreamUrlValidator.java
git commit -m "P1.1: route /api/payments/webhook through api-gateway, validate PAYMENT_SERVICE_URL at startup"
```

---

### Task 11: Deploy workflow scoping

**Files:**
- Modify: `.github/workflows/backend-deploy.yml`

**Interfaces:**
- Consumes: GitHub secrets `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `PAYMENT_SERVICE_URL` (must be added to the repo's GitHub Actions secrets before this workflow runs for real — out of scope for this plan to create, flag to the user).
- Produces: `payment-service` receives `RAZORPAY_*` + `SPRING_RABBITMQ_*` env vars on deploy; `api-gateway` receives `PAYMENT_SERVICE_URL`.

- [ ] **Step 1: Add FINAL_ variable resolution**

In `.github/workflows/backend-deploy.yml`, in the "Prepare Environment Variables" step, after the line `set_env "FINAL_TRIP_URL" "${{ secrets.TRIP_SERVICE_URL }}" "http://trip-tbd"` (currently line 106), add:

```yaml
          set_env "FINAL_PAYMENT_URL" "${{ secrets.PAYMENT_SERVICE_URL }}" "http://payment-tbd"
```

After the line `set_env "FINAL_QUOTE_TOKEN_SECRET" "${{ secrets.QUOTE_TOKEN_SECRET }}" "placeholder_secret"` (currently line 107), add:

```yaml
          set_env "FINAL_RAZORPAY_KEY_ID" "${{ secrets.RAZORPAY_KEY_ID }}" "placeholder_key_id"
          set_env "FINAL_RAZORPAY_KEY_SECRET" "${{ secrets.RAZORPAY_KEY_SECRET }}" "placeholder_secret"
          set_env "FINAL_RAZORPAY_WEBHOOK_SECRET" "${{ secrets.RAZORPAY_WEBHOOK_SECRET }}" "placeholder_secret"
```

- [ ] **Step 2: Scope RAZORPAY_* to payment-service only**

In the "Build service-scoped env vars" step, after the `QUOTE_TOKEN_SECRET` case block:

```bash
            # QUOTE_TOKEN_SECRET -> only the two services that sign/verify quote tokens.
            case "$SVC" in
              trip-service|payment-service)
                echo "QUOTE_TOKEN_SECRET=${FINAL_QUOTE_TOKEN_SECRET}" ;;
            esac
```

add:

```bash
            # RAZORPAY_* -> payment-service only (order creation + webhook verification).
            case "$SVC" in
              payment-service)
                echo "RAZORPAY_KEY_ID=${FINAL_RAZORPAY_KEY_ID}"
                echo "RAZORPAY_KEY_SECRET=${FINAL_RAZORPAY_KEY_SECRET}"
                echo "RAZORPAY_WEBHOOK_SECRET=${FINAL_RAZORPAY_WEBHOOK_SECRET}" ;;
            esac
```

- [ ] **Step 3: Add PAYMENT_SERVICE_URL to api-gateway's block**

In the same step, the existing `api-gateway)` case currently reads:

```bash
              api-gateway)
                echo "IDENTITY_SERVICE_URL=${FINAL_IDENTITY_URL}"
                echo "PACKAGE_SERVICE_URL=${FINAL_PACKAGE_URL}"
                echo "BOOKING_SERVICE_URL=${FINAL_BOOKING_URL}"
                echo "MEDIA_SERVICE_URL=${FINAL_MEDIA_URL}"
                echo "TRIP_SERVICE_URL=${FINAL_TRIP_URL}" ;;
```

Change it to:

```bash
              api-gateway)
                echo "IDENTITY_SERVICE_URL=${FINAL_IDENTITY_URL}"
                echo "PACKAGE_SERVICE_URL=${FINAL_PACKAGE_URL}"
                echo "BOOKING_SERVICE_URL=${FINAL_BOOKING_URL}"
                echo "MEDIA_SERVICE_URL=${FINAL_MEDIA_URL}"
                echo "TRIP_SERVICE_URL=${FINAL_TRIP_URL}"
                echo "PAYMENT_SERVICE_URL=${FINAL_PAYMENT_URL}" ;;
```

- [ ] **Step 4: Add payment-service to the RabbitMQ case**

The existing RabbitMQ case currently reads:

```bash
            # RabbitMQ -> only services with the amqp starter.
            case "$SVC" in
              booking-service|trip-service|reactive-booking-function)
```

Change the service list to:

```bash
            # RabbitMQ -> only services with the amqp starter.
            case "$SVC" in
              booking-service|trip-service|reactive-booking-function|payment-service)
```

- [ ] **Step 5: Validate YAML syntax**

Run: `cd "microservices/.." && python3 -c "import yaml; yaml.safe_load(open('.github/workflows/backend-deploy.yml'))" 2>&1 || echo "install pyyaml or eyeball the diff instead"`
Expected: no exception printed (if `python3`/`pyyaml` isn't available, visually re-check indentation matches the surrounding `case`/`esac` blocks exactly — this is a `run: |` shell block, not YAML structure, so a shell syntax error would only surface on next actual CI run).

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/backend-deploy.yml
git commit -m "P1.1: scope RAZORPAY_* and PAYMENT_SERVICE_URL env vars per A3 pattern, add payment-service to RabbitMQ scoping"
```

---

## After all tasks: final verification

Run the brief's own §9 verification commands and paste the output as part of hand-back:

```bash
cd microservices/payment-service

grep -rn 'SANDBOX-\|always succeeds' src/main --include='*.java' | grep -v -i test || echo "OK: no unconditional success on money path"
grep -rn 'webhook' src/main --include='*.java' | grep -i 'sign\|hmac\|verify' && echo "webhook signature check present"
grep -rn 'processed\|idempoten\|eventId\|event_id' src/main --include='*.java' && echo "idempotency present"
grep -rn 'RAZORPAY' src/main/resources/application.properties
grep -rn 'RAZORPAY' ../../.github/workflows/backend-deploy.yml

./mvnw -q clean test
```

Then hand back per the brief §11: files touched, the `PaymentProvider` interface, the `Payment` state machine, how raw-body signature verification and idempotency are implemented, the webhook exposure decision (gateway route), the §9 output, and the Razorpay API-shape assumptions flagged in Tasks 1 and 6 (SDK version, order-creation JSON shape, webhook payload shape, refund JSON shape) for the user to check against current Razorpay docs.
