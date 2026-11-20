package com.travel2go.backend.service;

/**
 * Charges a subscription fee through the existing Razorpay path (an explicitly
 * consented SUBSCRIPTION line item - never a booking/convenience fee, G1). Pluggable
 * so the real payment-service/Razorpay hop drops in; the default is a stub.
 */
public interface SubscriptionCharger {

    ChargeResult charge(String idempotencyKey, String userId, long amountPaise);

    record ChargeResult(boolean success, String providerChargeId) {
        public static ChargeResult ok(String id) {
            return new ChargeResult(true, id);
        }

        public static ChargeResult failed() {
            return new ChargeResult(false, null);
        }
    }
}
