package com.travel2go.backend.service;

import java.util.Date;
import java.util.UUID;

/**
 * Emitted (via the outbox) when a refund has not settled within its 72h SLA.
 * Stays in the existing payment.* namespace - no parallel refund.* namespace.
 */
public record RefundSlaBreachedEvent(String bookingRef, UUID refundId, Date slaDueAt) {
}
