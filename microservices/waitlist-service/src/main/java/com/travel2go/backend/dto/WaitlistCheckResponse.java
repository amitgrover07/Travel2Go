package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistCheckResponse {
    private double probability;
    private String verdict;
    private String explanation;
    private String shareToken;
    private String shareUrl;   // deep link for the "share on WhatsApp" affordance
}
