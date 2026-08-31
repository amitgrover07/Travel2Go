package com.travel2go.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests for the canonical QuoteTokenService (moved here from
 * payment-service during A1b). Because both trip-service and payment-service now
 * use this exact class, these tests guarantee the issuer and validator agree.
 */
class QuoteTokenServiceTest {

    private QuoteTokenService quoteTokenService;

    @BeforeEach
    void setUp() {
        quoteTokenService = new QuoteTokenService();
        ReflectionTestUtils.setField(quoteTokenService, "secret", "test-secret-key-for-quote-tokens-1234567890");
        ReflectionTestUtils.setField(quoteTokenService, "ttlMs", 900000L);
    }

    @Test
    void issuedTokenValidatesForSameLegAndPrice() {
        String token = quoteTokenService.issue("leg-1", 150000L);
        assertThat(quoteTokenService.isValid(token, "leg-1", 150000L)).isTrue();
    }

    @Test
    void tokenRejectedWhenPriceChanged() {
        String token = quoteTokenService.issue("leg-1", 150000L);
        assertThat(quoteTokenService.isValid(token, "leg-1", 150001L)).isFalse();
    }

    @Test
    void tokenRejectedWhenLegIdChanged() {
        String token = quoteTokenService.issue("leg-1", 150000L);
        assertThat(quoteTokenService.isValid(token, "leg-2", 150000L)).isFalse();
    }

    @Test
    void tamperedTokenRejected() {
        String token = quoteTokenService.issue("leg-1", 150000L);
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        assertThat(quoteTokenService.isValid(tampered, "leg-1", 150000L)).isFalse();
    }

    @Test
    void expiredTokenRejected() {
        ReflectionTestUtils.setField(quoteTokenService, "ttlMs", -1000L);
        String token = quoteTokenService.issue("leg-1", 150000L);
        assertThat(quoteTokenService.isValid(token, "leg-1", 150000L)).isFalse();
    }
}
