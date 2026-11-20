package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Optional "notify me" request attached to a check (F1 growth loop). Storing a
 * watch is allowed without an account; contact is optional so the affordance
 * never forces PII just to share.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "waitlist_watch")
public class WaitlistWatch {
    @DocumentId
    private String id;

    private String shareToken;
    private String contact;   // optional
    private String channel;   // WHATSAPP | EMAIL | SMS | null
    private String status;    // PENDING

    private Date createdAt;
}
