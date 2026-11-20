package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloatBalanceResponse {
    private long balancePaise;
    private boolean low;
    private long lowThresholdPaise;
}
