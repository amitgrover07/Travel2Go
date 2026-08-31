package com.travel2go.backend.webhook;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWebhookEventRepository extends FirestoreReactiveRepository<ProcessedWebhookEvent> {
}
