package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import java.time.LocalDateTime;

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
    
    private LocalDateTime bookingDate;
    private String status;
}
