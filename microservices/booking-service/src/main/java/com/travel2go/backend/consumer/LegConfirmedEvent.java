package com.travel2go.backend.consumer;

public record LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise) {
}
