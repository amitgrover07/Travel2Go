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
    private String status; // CREATED | CAPTURED | FAILED | REJECTED | REFUNDED

    private Long amountPaise;
    private Long feePaise; // MUST always be 0 (G1 - zero booking/convenience fee)

    private String providerRef; // provider ORDER id (set at CREATED)
    private String providerPaymentId; // provider PAYMENT id (set on CAPTURED, from the webhook)
    private Boolean quoteTokenValidated;
    private String ownerUserId; // captured from the JWT at createOrder time (P1.2)

    private Date createdAt;
}
