package com.travel2go.backend.provider;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PaymentResult {
    String status; // SUCCESS | FAILED
    String providerRef;
}
