package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeRequest {
    private String bookingRef;
    private Long amountPaise;
    private String method;
    private String quoteToken;
}
