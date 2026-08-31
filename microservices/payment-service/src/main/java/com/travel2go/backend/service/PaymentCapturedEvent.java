package com.travel2go.backend.service;

public record PaymentCapturedEvent(String bookingRef, String providerPaymentId, long amountPaise) {
}
