package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadAuditLog {
    private String adminName;
    private String action; // e.g. "STATUS_CHANGE", "EMAIL_SENT", "DETAILS_UPDATED"
    private String details;
    private Date timestamp;
}
