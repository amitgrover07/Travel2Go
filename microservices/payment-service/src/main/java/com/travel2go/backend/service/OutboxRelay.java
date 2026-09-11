package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.OutboxEntry;
import com.travel2go.backend.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Sole publisher of payment-service's domain events. Ticks every 1.5s,
 * pulling a small unpublished batch from the outbox (see OutboxRepository's
 * SKIP LOCKED query - safe under this service's multiple Cloud Run
 * instances) and publishing each. A publish failure leaves the row
 * unpublished and bumps its attempt count for the next tick - deliberately
 * no dead-lettering here, since giving up on a captured payment's
 * confirmation event is never acceptable; a permanently-down broker is an
 * ops incident, not something this relay should silently abandon.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 20;

    private final OutboxRepository outboxRepository;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1500)
    @Transactional
    public void relay() {
        List<OutboxEntry> batch = outboxRepository.findBatchForPublishing(BATCH_SIZE);
        for (OutboxEntry entry : batch) {
            try {
                Object event = deserialize(entry);
                eventPublisher.publish(entry.getType(), event);
                entry.setPublishedAt(new Date());
            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);
                log.error("Failed to relay outbox entry {} (type {}, attempt {}): {}",
                        entry.getId(), entry.getType(), entry.getAttempts(), e.getMessage(), e);
            }
            outboxRepository.save(entry);
        }
    }

    private Object deserialize(OutboxEntry entry) throws Exception {
        return switch (entry.getType()) {
            case "payment.captured" -> objectMapper.readValue(entry.getPayload(), PaymentCapturedEvent.class);
            case "payment.refunded" -> objectMapper.readValue(entry.getPayload(), PaymentRefundedEvent.class);
            default -> throw new IllegalStateException("Unknown outbox entry type: " + entry.getType());
        };
    }
}
