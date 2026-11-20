package com.travel2go.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only journey/booking status for an agent - composed from internal services,
 * never a passthrough of an internal endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentJourneyStatusResponse {
    private String legId;
    private String status;        // PENDING | CONFIRMED | CANCELLED | ... (leg/booking status)
    private Long amountPaise;
    private boolean available;    // false when the upstream status could not be resolved (circuit open)
}
