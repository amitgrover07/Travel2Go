package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    @Id
    private String id;
    
    private String email;
    private String password;
    
    // Auth provider (LOCAL, GOOGLE, FACEBOOK)
    private String provider;
    private String providerId;
    
    private Set<String> roles;
    private boolean enabled;
}
