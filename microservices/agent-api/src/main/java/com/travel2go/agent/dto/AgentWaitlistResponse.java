package com.travel2go.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentWaitlistResponse {
    private double probability;
    private String verdict;
    private String explanation;
    private String shareToken;
    private String shareUrl;
}
