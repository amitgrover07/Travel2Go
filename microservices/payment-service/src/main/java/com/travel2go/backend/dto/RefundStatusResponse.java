package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundStatusResponse {
    private String slaStatus;     // INITIATED | SETTLED | BREACHED
    private Date slaDueAt;
    private boolean breached;
    private String trigger;       // USER_CANCEL | DISRUPTION | MANUAL
    private Long amountPaise;
    private String providerStatus; // PENDING | SUCCESS | FAILED
}
