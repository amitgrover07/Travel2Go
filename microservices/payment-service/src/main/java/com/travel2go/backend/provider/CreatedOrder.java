package com.travel2go.backend.provider;

import lombok.Value;

@Value
public class CreatedOrder {
    String providerOrderId;
    String keyId;
    long amountPaise;
    String currency;
}
