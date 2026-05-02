package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.util.List;

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
    private String name;
    private String picture;
    
    // Auth provider (LOCAL, GOOGLE, FACEBOOK)
    private String provider;
    private String providerId;
    
    private List<String> roles;
    private boolean enabled;
}
