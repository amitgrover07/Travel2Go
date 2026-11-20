package com.travel2go.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentWaitlistRequest {
    private String trainNo;
    private String travelDate;   // ISO yyyy-MM-dd
    private String classCode;
    private Integer currentPosition;
}
