package com.travel2go.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flags refunds that have blown their 72h SLA. The actual transition
 * + event emission is idempotent in {@link PaymentService#flagSlaBreaches()}, so
 * running this on every instance is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundSlaMonitor {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.refund-sla.check-interval-ms:60000}")
    public void checkForBreaches() {
        try {
            paymentService.flagSlaBreaches();
        } catch (Exception e) {
            log.error("Refund SLA breach sweep failed: {}", e.getMessage(), e);
        }
    }
}
