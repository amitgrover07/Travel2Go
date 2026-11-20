package com.travel2go.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default v1 charger: records a successful charge without a live Razorpay call.
 * The real payment hop (payment-service / Razorpay subscription charge) replaces
 * this bean. PRD-OPEN: live subscription billing wiring.
 */
@Slf4j
@Component
public class DefaultSubscriptionCharger implements SubscriptionCharger {

    @Override
    public ChargeResult charge(String idempotencyKey, String userId, long amountPaise) {
        String chargeId = "sub_chg_" + UUID.randomUUID().toString().replace("-", "");
        log.info("Subscription charge {} for user {} amount {} paise (stub)", idempotencyKey, userId, amountPaise);
        return ChargeResult.ok(chargeId);
    }
}
