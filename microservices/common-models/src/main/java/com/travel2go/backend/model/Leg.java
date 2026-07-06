package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "legs")
public class Leg {
    @DocumentId
    private String id;

    private String tripId;
    private String type; // RAIL | BUS | HOTEL | FLIGHT | PACKAGE
    private String status; // SEARCHING | SELECTED | WAITLISTED | CONFIRMED | CANCELLED | COMPLETED | DISRUPTED

    private String supplierRef;
    private String pnr;
    private Double confirmationProbability;

    private Long pricePaise;
    private String quoteToken;

    private Date startAt;
    private Date endAt;

    private Map<String, Object> metadata;
}
