package com.travel2go.backend.provider;

public interface PaymentProvider {
    PaymentResult charge(String reference, long amountPaise, String method);
}
