package com.travel2go.backend.provider;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Real Razorpay integration - active by default (payment.provider unset or
 * =razorpay). Assumptions made against razorpay-java 1.4.x (verify against
 * current Razorpay docs before going live - see hand-back notes in
 * docs/superpowers/plans/2026-08-31-p1-1-payment-provider.md Task 6):
 *   - Orders API: client.orders.create(JSONObject) returns an Order whose
 *     "id" field is the provider order id.
 *   - Webhook signature: com.razorpay.Utils.verifyWebhookSignature(payload,
 *     signature, secret) throws RazorpayException on mismatch.
 *   - Refunds API: client.payments.refund(paymentId, JSONObject) returns a
 *     Refund whose "id" field is the provider refund id.
 *   - Webhook payload shape: { "event": "payment.captured"|"payment.failed",
 *     "payload": { "payment": { "entity": { "id", "order_id", "amount" } } } },
 *     signature carried in the "X-Razorpay-Signature" header - same shape
 *     WebhookPayloadParser and SandboxPaymentProvider already assume.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "razorpay", matchIfMissing = true)
public class RazorpayProvider implements PaymentProvider {

    static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    private RazorpayClient client;

    @PostConstruct
    void init() {
        requireNonBlank(keyId, "razorpay.key-id");
        requireNonBlank(keySecret, "razorpay.key-secret");
        requireNonBlank(webhookSecret, "razorpay.webhook-secret");
        try {
            client = new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new IllegalStateException("Failed to initialize Razorpay client", e);
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set. Refusing to start.");
        }
    }

    @Override
    public CreatedOrder createOrder(String reference, long amountPaise, String method) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", "INR");
            request.put("receipt", reference);
            request.put("payment_capture", 1);
            Order order = client.orders.create(request);
            return new CreatedOrder(order.get("id"), keyId, amountPaise, "INR");
        } catch (RazorpayException e) {
            throw new IllegalStateException("Razorpay order creation failed for " + reference, e);
        }
    }

    @Override
    public WebhookEvent verifyAndParse(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(SIGNATURE_HEADER);
        if (signature == null) {
            throw new InvalidWebhookSignatureException("missing " + SIGNATURE_HEADER + " header");
        }
        String payload = new String(rawBody, StandardCharsets.UTF_8);
        try {
            Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            throw new InvalidWebhookSignatureException("razorpay webhook signature invalid: " + e.getMessage());
        }
        return WebhookPayloadParser.parse(rawBody);
    }

    @Override
    public RefundResult refund(String providerPaymentId, long amountPaise) {
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            Refund refund = client.payments.refund(providerPaymentId, request);
            return new RefundResult("SUCCESS", refund.get("id"));
        } catch (RazorpayException e) {
            return new RefundResult("FAILED", null);
        }
    }
}
