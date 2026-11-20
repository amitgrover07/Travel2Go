package com.travel2go.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A price lock for an agent. The {@code quoteToken} is byte-for-byte identical in
 * format to the internal one issued by trip-service (same shared QuoteTokenService),
 * so any future agent booking re-validates price exactly like a first-party booking (G2).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentQuoteResponse {
    private String legId;
    private long pricePaise;
    private String currency;
    private String quoteToken;
    private long feePaise;   // always 0 - the traveller is never charged a booking fee (G1)
}
