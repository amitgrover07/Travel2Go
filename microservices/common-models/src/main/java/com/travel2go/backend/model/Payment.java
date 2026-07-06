package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "payments")
public class Payment {
    @DocumentId
    private String id;

    private String bookingRef;
    private String method; // UPI | CARD | NETBANKING
    private String status; // SUCCESS | FAILED

    private Long amountPaise;
    private Long feePaise; // MUST always be 0 (G1 - zero booking/convenience fee)

    private String providerRef;
    private Boolean quoteTokenValidated;

    private Date createdAt;
}
