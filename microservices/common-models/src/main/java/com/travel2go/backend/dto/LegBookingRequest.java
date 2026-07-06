package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegBookingRequest {
    private String tripId;
    private String legId;
    private String quoteToken;
    private Long amountPaise;
}
