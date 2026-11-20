package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscribeRequest {
    private String planCode;    // defaults to FAMILY_PLUS when null
    private String couponCode;  // optional (e.g. FOUNDING)
}
