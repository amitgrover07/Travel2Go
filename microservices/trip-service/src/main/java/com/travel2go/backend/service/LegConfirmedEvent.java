package com.travel2go.backend.service;

public record LegConfirmedEvent(String legId, String providerPaymentId, long amountPaise) {
}
