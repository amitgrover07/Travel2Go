package com.travel2go.backend.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Idempotency record: one row per provider payment id already captured, so a
 * re-delivered "payment.captured" webhook is a no-op instead of a
 * double-capture / double-publish. The primary key IS the atomic dedupe -
 * {@link ProcessedWebhookEventRepository#recordIfNew} inserts via
 * {@code ON CONFLICT (event_id) DO NOTHING} and reports whether a row was
 * actually inserted, rather than throwing on a duplicate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {
    @Id
    @Column(name = "event_id")
    private String id; // the provider payment id

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "processed_at", nullable = false)
    private Date processedAt;
}
