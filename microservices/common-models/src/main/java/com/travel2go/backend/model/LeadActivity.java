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
public class LeadActivity {
    private String activityId;
    private String adminName;
    private String type; // e.g. "Call", "Email", "Note", "Meeting"
    private String content;
    private Date timestamp;
}
