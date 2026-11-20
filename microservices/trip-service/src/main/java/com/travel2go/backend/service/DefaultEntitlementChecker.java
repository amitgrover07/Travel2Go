package com.travel2go.backend.service;

import org.springframework.stereotype.Component;

/**
 * Default: no entitlements (base caps apply). PRD-OPEN: the real implementation
 * calls subscription-service's /api/subscriptions/me/entitlements (cached in Redis).
 */
@Component
public class DefaultEntitlementChecker implements EntitlementChecker {

    @Override
    public boolean hasEntitlement(String userId, String entitlementKey) {
        return false;
    }
}
