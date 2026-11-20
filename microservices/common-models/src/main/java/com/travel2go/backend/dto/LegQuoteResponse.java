package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A server-priced quote for a leg: the authoritative price plus a signed quote-token,
 * both derived by trip-service (the price authority). Callers never supply the price,
 * so the token can only seal the real leg price (G2). feePaise is always 0 (G1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegQuoteResponse {
    private String legId;
    private long pricePaise;
    private String currency;
    private String quoteToken;
    private long feePaise;
}
