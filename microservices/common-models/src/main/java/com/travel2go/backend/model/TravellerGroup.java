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
@Document(collectionName = "traveller_groups")
public class TravellerGroup {
    @DocumentId
    private String id;

    // Existing fields - do not rename. ownerUserId is the organiser.
    private String ownerUserId;
    private String name;
    private List<String> travellerIds;

    // --- F8 Group travel additions ---
    private List<TravellerProfile> savedTravellerProfiles; // reusable name/ID records
    private String inviteToken;                            // share/join deep-link token
    private Date createdAt;
}
