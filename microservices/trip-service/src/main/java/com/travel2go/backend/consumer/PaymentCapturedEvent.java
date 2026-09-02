package com.travel2go.backend.consumer;

public record PaymentCapturedEvent(String bookingRef, String providerPaymentId, long amountPaise) {
}
