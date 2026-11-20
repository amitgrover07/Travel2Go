package com.travel2go.agent.service;

import com.travel2go.agent.client.TripQuoteClient;
import com.travel2go.agent.dto.AgentQuoteRequest;
import com.travel2go.agent.dto.AgentQuoteResponse;
import com.travel2go.backend.dto.LegQuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Returns a price-locked quote for an agent by delegating to trip-service, the
 * price authority. The facade holds no signing secret and never trusts a caller
 * price: trip-service signs the leg's real price and returns the token (G2), with
 * feePaise always 0 (G1).
 */
@Service
@RequiredArgsConstructor
public class AgentQuoteService {

    private final TripQuoteClient tripQuoteClient;

    public AgentQuoteResponse quote(AgentQuoteRequest request) {
        if (request.getLegId() == null || request.getLegId().isBlank()) {
            throw new IllegalArgumentException("legId is required");
        }

        LegQuoteResponse quote = tripQuoteClient.quoteLeg(request.getLegId());

        return AgentQuoteResponse.builder()
                .legId(quote.getLegId())
                .pricePaise(quote.getPricePaise())
                .currency(quote.getCurrency())
                .quoteToken(quote.getQuoteToken())
                .feePaise(quote.getFeePaise())   // trip-service already guarantees 0 (G1)
                .build();
    }
}
