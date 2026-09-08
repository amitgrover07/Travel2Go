package com.travel2go.backend.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, String> {

    /**
     * Atomically records a webhook event as processed via a native
     * INSERT ... ON CONFLICT DO NOTHING, so a duplicate delivery never
     * throws (and never degrades into a merge/update via save()'s
     * new-vs-existing detection). Returns the number of rows actually
     * inserted: 1 if this call recorded the event for the first time,
     * 0 if it was already recorded.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_webhook_events(event_id, processed_at) VALUES (:id, now()) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int recordIfNew(@Param("id") String eventId);
}
