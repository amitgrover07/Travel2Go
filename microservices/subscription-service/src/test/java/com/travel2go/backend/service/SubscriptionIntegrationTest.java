package com.travel2go.backend.service;

import com.travel2go.backend.model.Subscription;
import com.travel2go.backend.model.SubscriptionCharge;
import com.travel2go.backend.repository.SubscriptionChargeRepository;
import com.travel2go.backend.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F5 Family+, proven against a real Postgres: subscribe + entitlements, coupon
 * pricing, idempotent renewal (no double charge), dunning grace, cancel-then-expire,
 * and the G1 regression (a non-subscriber has no entitlements and no charge).
 */
@Testcontainers
@SpringBootTest
class SubscriptionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "test-jwt-secret-0123456789abcdef0123456789abcdef");
    }

    @Autowired private SubscriptionService subscriptionService;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionChargeRepository chargeRepository;

    @MockitoBean private SubscriptionCharger charger;

    private void chargerSucceeds() {
        when(charger.charge(anyString(), anyString(), anyLong()))
                .thenAnswer(inv -> SubscriptionCharger.ChargeResult.ok("chg_" + UUID.randomUUID()));
    }

    @Test
    void subscribe_grantsEntitlements_andChargesOnceAtPlanPrice() {
        chargerSucceeds();

        Subscription sub = subscriptionService.subscribe("u-fam-1", "FAMILY_PLUS", null);

        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getPricePaise()).isEqualTo(49900L); // INR 499/yr, a SUBSCRIPTION line item (G1)
        assertThat(subscriptionService.entitlements("u-fam-1"))
                .contains("WAITLIST_AUTO_REBOOK", "FREE_CANCEL_48H", "GROUP_UP_TO_9",
                        "PRIORITY_REFUND_72H", "LOCAL_LANGUAGE_SUPPORT");

        List<SubscriptionCharge> charges = chargeRepository.findBySubscriptionId(sub.getId());
        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(charges.get(0).getAmountPaise()).isEqualTo(49900L);
        verify(charger, times(1)).charge(anyString(), eq("u-fam-1"), anyLong());
    }

    @Test
    void couponReducesPriceTo299_withoutCodeChange() {
        chargerSucceeds();

        Subscription sub = subscriptionService.subscribe("u-fam-2", "FAMILY_PLUS", "FOUNDING");

        assertThat(sub.getPricePaise()).isEqualTo(29900L); // INR 299 founding offer, from data not code
        assertThat(sub.getCouponCode()).isEqualTo("FOUNDING");
    }

    @Test
    void nonSubscriber_hasNoEntitlements_andNoCharge() {
        // G1 regression: a non-subscriber gets nothing - the free path never charges them.
        assertThat(subscriptionService.entitlements("u-none")).isEmpty();
        assertThat(subscriptionRepository.findByUserId("u-none")).isEmpty();
    }

    @Test
    void renewalRetryForSamePeriod_doesNotDoubleCharge() {
        chargerSucceeds();
        Subscription sub = subscriptionService.subscribe("u-fam-3", "FAMILY_PLUS", null);
        Date afterSubscribe = sub.getCurrentPeriodEnd();

        subscriptionService.renew(sub.getId(), 2);            // bills period 2
        subscriptionService.renew(sub.getId(), 2);            // webhook redelivery - must be a no-op

        List<SubscriptionCharge> charges = chargeRepository.findBySubscriptionId(sub.getId());
        assertThat(charges).hasSize(2); // period 1 (subscribe) + period 2 only
        verify(charger, times(1)).charge(eq(sub.getId() + ":2"), anyString(), anyLong());

        Subscription reloaded = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(reloaded.getPeriodIndex()).isEqualTo(2);
        assertThat(reloaded.getCurrentPeriodEnd()).isAfter(afterSubscribe); // advanced exactly once
    }

    @Test
    void failedRenewal_entersGrace_andEntitlementsPersistInGrace() {
        when(charger.charge(contains(":1"), anyString(), anyLong()))
                .thenReturn(SubscriptionCharger.ChargeResult.ok("chg_initial"));
        when(charger.charge(contains(":2"), anyString(), anyLong()))
                .thenReturn(SubscriptionCharger.ChargeResult.failed());

        Subscription sub = subscriptionService.subscribe("u-fam-4", "FAMILY_PLUS", null);
        subscriptionService.renew(sub.getId(), 2);

        Subscription reloaded = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("PAST_DUE");
        assertThat(reloaded.getGraceUntil()).isNotNull();
        // Entitlements survive during the grace window (dunning).
        assertThat(subscriptionService.entitlements("u-fam-4")).isNotEmpty();
    }

    @Test
    void cancel_keepsEntitlementsUntilPeriodEnd_thenExpires() {
        chargerSucceeds();
        Subscription sub = subscriptionService.subscribe("u-fam-5", "FAMILY_PLUS", null);

        subscriptionService.cancel(sub.getId(), "u-fam-5");
        Subscription cancelled = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.isAutoRenew()).isFalse();
        // Still entitled until the paid period ends.
        assertThat(subscriptionService.entitlements("u-fam-5")).isNotEmpty();

        // Force the period to have ended: entitlements expire (fail-closed).
        cancelled.setCurrentPeriodEnd(new Date(System.currentTimeMillis() - 86_400_000L));
        subscriptionRepository.save(cancelled);
        assertThat(subscriptionService.entitlements("u-fam-5")).isEmpty();
    }
}
