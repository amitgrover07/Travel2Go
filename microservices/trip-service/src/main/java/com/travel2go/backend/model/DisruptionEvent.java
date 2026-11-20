package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Records that a disruption for a (legId, sourceEventId) was handled, and how.
 * The document id is deterministic ({@code legId:sourceEventId}), so re-delivery
 * of the same disruption is handled exactly once (idempotency key).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "disruption_events")
public class DisruptionEvent {
    @DocumentId
    private String id; // legId:sourceEventId

    private String legId;
    private String sourceEventId;
    private String reason;
    private String outcome;          // REBOOKED | REFUNDED | CONSENT_REQUIRED | IGNORED
    private String rebookedToLegId;  // set when REBOOKED
    private Date handledAt;
}
