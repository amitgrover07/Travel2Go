package com.travel2go.backend.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * If payment-service is unreachable we must NOT silently mark a disruption handled,
 * so the fallback rethrows: the disruption is left unrecorded and a retried delivery
 * re-attempts the refund (idempotent per payment in F3).
 */
@Component
public class PaymentRefundClientFallbackFactory implements FallbackFactory<PaymentRefundClient> {

    @Override
    public PaymentRefundClient create(Throwable cause) {
        return (bookingRef, trigger) -> {
            throw new RefundDispatchException("payment-service unavailable for refund of " + bookingRef, cause);
        };
    }
}
