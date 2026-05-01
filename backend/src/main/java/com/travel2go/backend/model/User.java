package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "users")
public class User {
    @DocumentId
    private String id;
    
    private String email;
    private String password;
    
    // Auth provider (LOCAL, GOOGLE, FACEBOOK)
    private String provider;
    private String providerId;
    
    private Set<String> roles;
    private boolean enabled;
}
