package com.travel2go.backend.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPaymentProviderTest {

    private SandboxPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SandboxPaymentProvider();
        ReflectionTestUtils.setField(provider, "webhookSecret", "test-sandbox-webhook-secret-1234567890");
    }

    @Test
    void createOrder_returnsDeterministicSandboxOrder() {
        CreatedOrder order = provider.createOrder("leg-1", 150000L, "UPI");

        assertThat(order.getProviderOrderId()).startsWith("sandbox_order_");
        assertThat(order.getAmountPaise()).isEqualTo(150000L);
        assertThat(order.getCurrency()).isEqualTo("INR");
    }

    @Test
    void verifyAndParse_acceptsCorrectlySignedCapturedPayload() {
        byte[] payload = ("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":150000}}}}")
                .getBytes(StandardCharsets.UTF_8);
        String signature = provider.sign(payload);

        WebhookEvent event = provider.verifyAndParse(payload, Map.of("X-Razorpay-Signature", signature));

        assertThat(event.getType()).isEqualTo(WebhookEventType.CAPTURED);
        assertThat(event.getProviderOrderId()).isEqualTo("order_1");
        assertThat(event.getProviderPaymentId()).isEqualTo("pay_1");
        assertThat(event.getAmountPaise()).isEqualTo(150000L);
    }

    @Test
    void verifyAndParse_rejectsTamperedSignature() {
        byte[] payload = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String badSignature = provider.sign(payload) + "tampered";

        assertThatThrownBy(() -> provider.verifyAndParse(payload, Map.of("X-Razorpay-Signature", badSignature)))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verifyAndParse_rejectsMissingSignatureHeader() {
        byte[] payload = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider.verifyAndParse(payload, Map.of()))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void refund_returnsDeterministicSuccess() {
        RefundResult result = provider.refund("pay_1", 150000L);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getProviderRefundId()).startsWith("sandbox_refund_");
    }
}
