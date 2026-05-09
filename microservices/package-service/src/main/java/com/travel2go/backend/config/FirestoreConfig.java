package com.travel2go.backend.config;

import com.google.cloud.spring.data.firestore.FirestoreOptionsCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirestoreConfig {

    @Value("${spring.cloud.gcp.firestore.database-id:travel2go-db}")
    private String databaseId;

    @Bean
    public FirestoreOptionsCustomizer firestoreOptionsCustomizer() {
        return builder -> {
            builder.setDatabaseId(databaseId);
        };
    }
}
