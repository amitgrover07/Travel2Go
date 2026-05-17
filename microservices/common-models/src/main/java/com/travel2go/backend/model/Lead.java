package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "leads")
public class Lead {
    @DocumentId
    private String id;
    
    // Customer Info
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;
    
    // Package Info
    private String packageId;
    private String packageTitle;
    private String packageCode;
    
    // Pricing Info
    private Double basePrice;
    private Double discountPercentage;
    private Double finalPrice;
    
    // Lead Metadata
    private Date leadDate;
    private String status; // NEW, CONTACTED, PROPOSAL_SENT, NEGOTIATION, CONVERTED, CLOSED_LOST
    private String source; // e.g., EMAIL_SENT, ADMIN_CREATED
    private String bestTimeToReach;
    private String notes;
}
