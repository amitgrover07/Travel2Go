package com.travel2go.backend.repository;

import com.travel2go.backend.model.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, UUID> {
    /**
     * SKIP LOCKED is required, not optional: payment-service runs up to 3
     * Cloud Run instances, so more than one relay poller can be ticking
     * concurrently against this table. A row already locked by another
     * instance's in-flight publish is simply excluded from this batch
     * rather than blocking - it will be picked up by whichever instance
     * finishes first, on its next tick.
     */
    @Query(value = "SELECT * FROM outbox WHERE published_at IS NULL "
            + "ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEntry> findBatchForPublishing(@Param("limit") int limit);
}
