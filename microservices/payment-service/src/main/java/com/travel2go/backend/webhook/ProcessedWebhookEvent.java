package com.travel2go.backend.webhook;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Idempotency record: one document per provider payment id already captured,
 * so a re-delivered "payment.captured" webhook is a no-op instead of a
 * double-capture / double-publish.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "processed_webhook_events")
public class ProcessedWebhookEvent {
    @DocumentId
    private String id; // the provider payment id

    private Date processedAt;
}
