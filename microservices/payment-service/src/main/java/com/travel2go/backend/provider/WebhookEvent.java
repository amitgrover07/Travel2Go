package com.travel2go.backend.provider;

import lombok.Value;

@Value
public class WebhookEvent {
    WebhookEventType type;
    String providerOrderId;
    String providerPaymentId;
    long amountPaise;
}
