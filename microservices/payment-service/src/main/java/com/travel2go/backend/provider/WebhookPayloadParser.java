package com.travel2go.backend.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Shared by SandboxPaymentProvider and RazorpayProvider so both parse the
 * assumed Razorpay webhook JSON shape identically - only signature
 * verification differs between them.
 */
final class WebhookPayloadParser {

    static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebhookPayloadParser() {
    }

    static WebhookEvent parse(byte[] rawBody) {
        JsonNode root;
        try {
            root = MAPPER.readTree(rawBody);
        } catch (IOException e) {
            throw new UncheckedIOException("Malformed webhook payload", e);
        }

        String event = root.path("event").asText("");
        JsonNode entity = root.path("payload").path("payment").path("entity");

        WebhookEventType type;
        if ("payment.captured".equals(event)) {
            type = WebhookEventType.CAPTURED;
        } else if ("payment.failed".equals(event)) {
            type = WebhookEventType.FAILED;
        } else {
            type = WebhookEventType.OTHER;
        }

        return new WebhookEvent(
                type,
                entity.path("order_id").asText(null),
                entity.path("id").asText(null),
                entity.path("amount").asLong(0L));
    }
}
