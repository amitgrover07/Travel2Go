package com.travel2go.backend.service;

import com.travel2go.backend.model.Coupon;
import com.travel2go.backend.model.Plan;
import com.travel2go.backend.model.Subscription;
import com.travel2go.backend.model.SubscriptionCharge;
import com.travel2go.backend.repository.CouponRepository;
import com.travel2go.backend.repository.PlanRepository;
import com.travel2go.backend.repository.SubscriptionChargeRepository;
import com.travel2go.backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plans, subscriptions and entitlements (F5). A subscription charge is an
 * explicitly-consented SUBSCRIPTION line item, never a booking fee - the free
 * booking path stays free for non-subscribers (G1). Billing is idempotent per
 * (subscription, period), so a renewal retry never double-charges; a failed
 * renewal enters a grace period (dunning). Entitlement checks are date-based and
 * fail-closed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private enum ChargeOutcome { CHARGED, ALREADY_DONE, FAILED }

    private final PlanRepository planRepository;
    private final CouponRepository couponRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChargeRepository chargeRepository;
    private final SubscriptionCharger charger;

    @Value("${subscription.grace-days:7}")
    private int graceDays;

    @Transactional
    public Subscription subscribe(String userId, String planCode, String couponCode) {
        // Idempotent: an existing live subscription for the user is returned as-is.
        Optional<Subscription> existing = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (existing.isPresent() && isLive(existing.get())) {
            return existing.get();
        }

        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan: " + planCode));

        long price = plan.getPricePaise();
        String appliedCoupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            // Coupon must be valid for THIS plan - a plan's promo can't discount another plan.
            Coupon coupon = couponRepository.findByCodeAndPlanCodeAndActiveTrue(couponCode, plan.getCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid coupon " + couponCode + " for plan " + plan.getCode()));
            price = coupon.getPricePaise();
            appliedCoupon = coupon.getCode();
        }

        Date now = new Date();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .userId(userId)
                .planId(plan.getId())
                .status("ACTIVE")
                .currentPeriodEnd(plusDays(now, plan.getPeriodDays()))
                .pricePaise(price)
                .couponCode(appliedCoupon)
                .periodIndex(1)
                .autoRenew(true)
                .createdAt(now)
                .build());

        ChargeOutcome outcome = charge(subscription, subscription.getId() + ":1", price, userId);
        if (outcome == ChargeOutcome.FAILED) {
            // Roll back the whole subscription: no grant without the first payment.
            throw new IllegalStateException("Subscription charge failed for user " + userId);
        }
        return subscription;
    }

    /**
     * Renewal for a specific billing period, driven by the billing webhook. Idempotent:
     * the webhook carries the period it is billing, so a redelivery for an already-billed
     * period is a no-op (never a double charge). A failed charge enters a grace period.
     * When {@code periodIndex} is null, the next period is billed.
     */
    @Transactional
    public Subscription renew(UUID subscriptionId, Integer periodIndex) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
        if ("CANCELLED".equals(sub.getStatus()) || !sub.isAutoRenew()) {
            return sub; // no further billing
        }

        int targetPeriod = periodIndex != null ? periodIndex : sub.getPeriodIndex() + 1;
        if (targetPeriod <= sub.getPeriodIndex()) {
            return sub; // already billed this (or an earlier) period - idempotent no-op
        }

        Plan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan missing for subscription " + subscriptionId));
        String key = sub.getId() + ":" + targetPeriod;

        ChargeOutcome outcome = charge(sub, key, plan.getPricePaise(), sub.getUserId());
        if (outcome == ChargeOutcome.ALREADY_DONE) {
            return sub; // retry of an already-billed period: no state change, no double charge
        }
        if (outcome == ChargeOutcome.FAILED) {
            // Dunning: enter grace; entitlements survive until graceUntil.
            sub.setStatus("PAST_DUE");
            sub.setGraceUntil(plusDays(sub.getCurrentPeriodEnd(), graceDays));
            return subscriptionRepository.save(sub);
        }

        sub.setPeriodIndex(targetPeriod);
        sub.setCurrentPeriodEnd(plusDays(sub.getCurrentPeriodEnd(), plan.getPeriodDays()));
        sub.setStatus("ACTIVE");
        sub.setGraceUntil(null);
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public Subscription cancel(UUID subscriptionId, String userId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
        if (!sub.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your subscription");
        }
        sub.setAutoRenew(false);
        sub.setStatus("CANCELLED");
        // Entitlements remain until currentPeriodEnd (enforced in entitlements()).
        return subscriptionRepository.save(sub);
    }

    public Optional<Subscription> getMine(String userId) {
        return subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Fail-closed, date-based entitlements. Empty unless a subscription is currently in force. */
    public List<String> entitlements(String userId) {
        Optional<Subscription> maybe = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (maybe.isEmpty() || !inForce(maybe.get())) {
            return List.of();
        }
        return planRepository.findById(maybe.get().getPlanId())
                .map(Plan::entitlementKeys)
                .orElse(List.of());
    }

    public boolean hasEntitlement(String userId, String entitlement) {
        return entitlements(userId).contains(entitlement);
    }

    private ChargeOutcome charge(Subscription sub, String idempotencyKey, long amountPaise, String userId) {
        int claimed = chargeRepository.insertIfNew(UUID.randomUUID(), idempotencyKey, sub.getId(), amountPaise);
        if (claimed == 0) {
            boolean ok = chargeRepository.findByIdempotencyKey(idempotencyKey)
                    .map(c -> "SUCCESS".equals(c.getStatus()))
                    .orElse(false);
            return ok ? ChargeOutcome.ALREADY_DONE : ChargeOutcome.FAILED;
        }
        SubscriptionCharger.ChargeResult result = charger.charge(idempotencyKey, userId, amountPaise);
        chargeRepository.complete(idempotencyKey, result.success() ? "SUCCESS" : "FAILED", result.providerChargeId());
        return result.success() ? ChargeOutcome.CHARGED : ChargeOutcome.FAILED;
    }

    /** ACTIVE and not past its period, or PAST_DUE within grace - i.e. billing is current-ish. */
    private boolean isLive(Subscription sub) {
        Date now = new Date();
        if ("ACTIVE".equals(sub.getStatus())) {
            return !now.after(sub.getCurrentPeriodEnd());
        }
        if ("PAST_DUE".equals(sub.getStatus())) {
            return sub.getGraceUntil() != null && !now.after(sub.getGraceUntil());
        }
        return false;
    }

    /** Entitlements are in force: live, OR cancelled but still within the paid period. */
    private boolean inForce(Subscription sub) {
        if (isLive(sub)) {
            return true;
        }
        if ("CANCELLED".equals(sub.getStatus())) {
            return !new Date().after(sub.getCurrentPeriodEnd());
        }
        return false;
    }

    private Date plusDays(Date from, int days) {
        return Date.from(from.toInstant().plus(days, ChronoUnit.DAYS));
    }
}
