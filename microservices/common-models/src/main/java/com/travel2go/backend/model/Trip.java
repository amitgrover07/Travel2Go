package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "trips")
public class Trip {
    @DocumentId
    private String id;

    private String ownerUserId;
    private String title;
    private String status; // DRAFT | BOOKED | IN_PROGRESS | COMPLETED | CANCELLED

    private List<String> travellerIds;
    private List<String> legIds;

    private String origin;
    private String destination;

    private Date startDate;
    private Date endDate;

    private Long budgetPaise;
    private Long savingsLedgerPaise;

    private Date createdAt;
    private Date updatedAt;
}
