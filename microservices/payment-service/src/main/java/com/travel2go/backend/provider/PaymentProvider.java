package com.travel2go.backend.provider;

import java.util.Map;

public interface PaymentProvider {

    CreatedOrder createOrder(String reference, long amountPaise, String method);

    /**
     * Verifies the raw webhook body's signature and parses it.
     * @throws InvalidWebhookSignatureException if the signature is missing or invalid.
     */
    WebhookEvent verifyAndParse(byte[] rawBody, Map<String, String> headers);

    RefundResult refund(String providerPaymentId, long amountPaise);
}
