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
@Document(collectionName = "travellers")
public class Traveller {
    @DocumentId
    private String id;

    private String ownerUserId;
    private String fullName;
    private Date dateOfBirth;

    private String idType; // AADHAAR | PASSPORT | DL | OTHER
    private String idReferenceToken; // tokenised reference only — never the raw ID (DPDP)

    private String gender;
    private String relationship; // SELF | SPOUSE | CHILD | PARENT | OTHER
    private Boolean isMinor;
}
