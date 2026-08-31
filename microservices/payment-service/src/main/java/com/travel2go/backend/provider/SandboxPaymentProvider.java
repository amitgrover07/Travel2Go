package com.travel2go.backend.provider;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic PaymentProvider for local/test use. Performs a real
 * HMAC-SHA256 signature check against a local secret so the
 * signature-rejection branch is exercised the same way RazorpayProvider
 * exercises it, without needing live Razorpay credentials.
 *
 * Active when payment.provider=sandbox (see application-local.properties).
 * Never active in the default (prod) profile - RazorpayProvider is.
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "sandbox")
public class SandboxPaymentProvider implements PaymentProvider {

    private static final String HMAC_ALGO = "HmacSHA256";
    static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @PostConstruct
    void validateSecret() {
        int len = webhookSecret == null ? 0 : webhookSecret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "razorpay.webhook-secret must be set and at least 32 bytes (was " + len + "). Refusing to start.");
        }
    }

    @Override
    public CreatedOrder createOrder(String reference, long amountPaise, String method) {
        return new CreatedOrder("sandbox_order_" + UUID.randomUUID(), "sandbox_key_id", amountPaise, "INR");
    }

    @Override
    public WebhookEvent verifyAndParse(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(SIGNATURE_HEADER);
        if (signature == null || !sign(rawBody).equals(signature)) {
            throw new InvalidWebhookSignatureException("sandbox webhook signature mismatch");
        }
        return WebhookPayloadParser.parse(rawBody);
    }

    @Override
    public RefundResult refund(String providerPaymentId, long amountPaise) {
        return new RefundResult("SUCCESS", "sandbox_refund_" + UUID.randomUUID());
    }

    /** Test/local helper: signs a payload the same way verifyAndParse expects it signed. */
    public String sign(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(rawBody);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign sandbox webhook payload", e);
        }
    }
}
