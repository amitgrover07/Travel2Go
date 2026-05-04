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
@Document(collectionName = "verification_codes")
public class VerificationCode {
    @DocumentId
    private String id;
    private String email;
    private String phone;
    private String code;
    private Date expiryDate;
    
    public boolean isExpired() {
        return new Date().after(this.expiryDate);
    }
}
