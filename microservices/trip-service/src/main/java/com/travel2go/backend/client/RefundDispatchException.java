package com.travel2go.backend.client;

/** The disruption refund could not be dispatched to payment-service (circuit open/timeout). */
public class RefundDispatchException extends RuntimeException {
    public RefundDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
