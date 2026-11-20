package com.travel2go.backend.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Triggers an F3 refund when a disrupted leg has no alternative. Circuit breaker +
 * timeout via the shared Resilience4j config. The refund is idempotent per payment
 * (F3), so a retried disruption never double-refunds.
 *
 * PRD-OPEN: service-to-service auth. payment-service's refund endpoint is ROLE_ADMIN;
 * a dedicated service credential needs wiring before the live hop works.
 */
@FeignClient(name = "payment-service", url = "${PAYMENT_SERVICE_URL:http://localhost:8083}",
        contextId = "paymentRefundClient",
        fallbackFactory = PaymentRefundClientFallbackFactory.class)
public interface PaymentRefundClient {

    @PostMapping("/api/payments/{bookingRef}/refund")
    void refund(@PathVariable("bookingRef") String bookingRef, @RequestParam("trigger") String trigger);
}
