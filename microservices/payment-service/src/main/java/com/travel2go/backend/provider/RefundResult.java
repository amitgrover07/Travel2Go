package com.travel2go.backend.provider;

import lombok.Value;

@Value
public class RefundResult {
    String status; // SUCCESS | FAILED
    String providerRefundId;
}
