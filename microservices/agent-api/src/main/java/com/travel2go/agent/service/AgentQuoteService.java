package com.travel2go.agent.service;

import com.travel2go.agent.dto.AgentQuoteRequest;
import com.travel2go.agent.dto.AgentQuoteResponse;
import com.travel2go.backend.service.QuoteTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issues a price-locked quote for an agent using the SAME shared QuoteTokenService
 * as trip-service, so the token is identical in format and re-validates server-side
 * on any future booking (G2). The traveller fee is always zero (G1).
 */
@Service
@RequiredArgsConstructor
public class AgentQuoteService {

    private final QuoteTokenService quoteTokenService;

    public AgentQuoteResponse quote(AgentQuoteRequest request) {
        if (request.getLegId() == null || request.getLegId().isBlank()) {
            throw new IllegalArgumentException("legId is required");
        }
        if (request.getPricePaise() == null || request.getPricePaise() < 0) {
            throw new IllegalArgumentException("pricePaise must be a non-negative amount in paise");
        }
        long pricePaise = request.getPricePaise();
        String currency = (request.getCurrency() == null || request.getCurrency().isBlank())
                ? "INR" : request.getCurrency();

        String quoteToken = quoteTokenService.issue(request.getLegId(), pricePaise);

        return AgentQuoteResponse.builder()
                .legId(request.getLegId())
                .pricePaise(pricePaise)
                .currency(currency)
                .quoteToken(quoteToken)
                .feePaise(0L)   // G1: never a booking/convenience fee
                .build();
    }
}
