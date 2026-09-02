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
@Document(collectionName = "bookings")
public class Booking {
    @DocumentId
    private String id;

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;

    private String packageId;
    private String packageTitle;

    private Date bookingDate;
    private String status;

    // Leg-level booking fields (trip-service orchestration) - null for legacy package bookings
    private String tripId;
    private String legId;
    private String quoteToken;
    private Long amountPaise;
    private Long feePaise;
    private String ownerUserId; // captured from the JWT at create time (P1.2)
    private String providerPaymentId; // set on CONFIRMED, from the payment.captured event (P1.2)
    private Date confirmedAt; // set on CONFIRMED (P1.2)
}
