package com.travel2go.backend.service;

/**
 * Checks a user's subscription entitlements (owned by subscription-service, F5).
 * Pluggable so the real cross-service lookup drops in; the default denies, which
 * keeps the base (non-subscriber) caps in force.
 */
public interface EntitlementChecker {
    boolean hasEntitlement(String userId, String entitlementKey);
}
