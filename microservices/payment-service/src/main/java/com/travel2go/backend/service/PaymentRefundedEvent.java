package com.travel2go.backend.service;

public record PaymentRefundedEvent(String bookingRef, String providerRefundId, long amountPaise) {
}
