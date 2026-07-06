package com.travel2go.backend.config;

import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirestoreConfig {

    @Value("${spring.cloud.gcp.firestore.project-id:travel2go-495007}")
    private String projectId;

    @Value("${spring.cloud.gcp.firestore.database-id:travel2go-db}")
    private String databaseId;

    @Bean
    public FirestoreOptions firestoreOptions() {
        return FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setDatabaseId(databaseId)
                .build();
    }
}
