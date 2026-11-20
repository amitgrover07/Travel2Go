package com.travel2go.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentQuoteRequest {
    private String legId;
    private Long pricePaise;
    private String currency;   // optional, defaults to INR
}
