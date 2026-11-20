package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * A single public waitlist check (F1). No PII: the originating IP is stored only
 * as a salted hash, and no account is required. The persisted probability/verdict/
 * explanation make a shared link reproduce the exact original result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "waitlist_checks")
public class WaitlistCheck {
    @DocumentId
    private String id;

    private String trainNo;
    private String travelDate;   // ISO yyyy-MM-dd as supplied
    private String classCode;
    private Integer currentPosition;

    private Double estimateProbability;
    private String verdict;
    private String explanation;

    private String sourceIp;     // SHA-256 hash, never the raw address
    private String shareToken;

    private Date createdAt;
}
