package com.travel2go.agent.service;

import com.travel2go.agent.dto.AgentQuoteRequest;
import com.travel2go.agent.dto.AgentQuoteResponse;
import com.travel2go.backend.service.QuoteTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the F2 AC: the agent quote-token is identical in format to the internal
 * one and validates server-side. The agent issues with the shared QuoteTokenService;
 * a SEPARATE QuoteTokenService instance configured with the same secret (standing in
 * for trip-service) accepts it - exactly, and rejects any price tamper (G2).
 */
class QuoteTokenParityTest {

    private static final String SECRET = "shared-quote-secret-1234567890-abcdefgh";

    private AgentQuoteService agentQuoteService;
    private QuoteTokenService tripSideValidator;

    @BeforeEach
    void setUp() {
        QuoteTokenService issuer = new QuoteTokenService();
        ReflectionTestUtils.setField(issuer, "secret", SECRET);
        ReflectionTestUtils.setField(issuer, "ttlMs", 900000L);
        agentQuoteService = new AgentQuoteService(issuer);

        tripSideValidator = new QuoteTokenService();
        ReflectionTestUtils.setField(tripSideValidator, "secret", SECRET);
        ReflectionTestUtils.setField(tripSideValidator, "ttlMs", 900000L);
    }

    @Test
    void agentTokenValidatesOnTheTripSideAndCarriesNoFee() {
        AgentQuoteResponse quote = agentQuoteService.quote(
                AgentQuoteRequest.builder().legId("leg-9").pricePaise(275000L).build());

        // JWT format: three dot-separated segments, same as the internal token.
        assertThat(quote.getQuoteToken().split("\\.")).hasSize(3);
        assertThat(quote.getFeePaise()).isZero();                 // G1
        assertThat(quote.getCurrency()).isEqualTo("INR");

        // G2 parity: the trip-side validator accepts the exact leg+price, rejects a tamper.
        assertThat(tripSideValidator.isValid(quote.getQuoteToken(), "leg-9", 275000L)).isTrue();
        assertThat(tripSideValidator.isValid(quote.getQuoteToken(), "leg-9", 275001L)).isFalse();
    }

    @Test
    void rejectsMissingLegOrNegativePrice() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> agentQuoteService.quote(AgentQuoteRequest.builder().pricePaise(1000L).build()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> agentQuoteService.quote(AgentQuoteRequest.builder().legId("leg-1").pricePaise(-5L).build()));
    }
}
