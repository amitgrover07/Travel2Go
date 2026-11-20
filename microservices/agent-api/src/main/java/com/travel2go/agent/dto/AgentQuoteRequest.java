package com.travel2go.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An agent asks to quote a leg by id only. The price is NEVER supplied by the
 * caller - trip-service (the price authority) resolves and signs the real leg
 * price, so an agent can never choose the price sealed into the quote-token (G2).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentQuoteRequest {
    private String legId;
}
