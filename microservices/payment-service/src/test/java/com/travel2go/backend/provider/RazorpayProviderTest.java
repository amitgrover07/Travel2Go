package com.travel2go.backend.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers only verifyAndParse - RazorpayProvider.init() is never called here
 * (it would construct a live RazorpayClient), so webhookSecret is injected
 * directly via reflection, the same field verifyAndParse reads.
 */
class RazorpayProviderTest {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String WEBHOOK_SECRET = "test-razorpay-webhook-secret-1234567890";

    private RazorpayProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RazorpayProvider();
        ReflectionTestUtils.setField(provider, "webhookSecret", WEBHOOK_SECRET);
    }

    private static String sign(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(rawBody);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test payload", e);
        }
    }

    @Test
    void verifyAndParse_acceptsCorrectlySignedCapturedPayload() {
        byte[] payload = ("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":150000}}}}")
                .getBytes(StandardCharsets.UTF_8);
        String signature = sign(payload);

        WebhookEvent event = provider.verifyAndParse(payload, Map.of("X-Razorpay-Signature", signature));

        assertThat(event.getType()).isEqualTo(WebhookEventType.CAPTURED);
        assertThat(event.getProviderOrderId()).isEqualTo("order_1");
        assertThat(event.getProviderPaymentId()).isEqualTo("pay_1");
        assertThat(event.getAmountPaise()).isEqualTo(150000L);
    }

    @Test
    void verifyAndParse_rejectsTamperedSignature() {
        byte[] payload = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String badSignature = sign(payload) + "tampered";

        assertThatThrownBy(() -> provider.verifyAndParse(payload, Map.of("X-Razorpay-Signature", badSignature)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verifyAndParse_rejectsMissingSignatureHeader() {
        byte[] payload = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider.verifyAndParse(payload, Map.of()))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
