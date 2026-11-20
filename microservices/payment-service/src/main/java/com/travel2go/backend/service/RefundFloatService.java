package com.travel2go.backend.service;

import com.travel2go.backend.model.RefundFloatLedger;
import com.travel2go.backend.repository.RefundFloatLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

/**
 * The ring-fenced refund float: an internal liability ledger held separate from
 * operating funds. Every refund debits it; every top-up credits it. The reported
 * balance is always the signed SUM over the ledger, so it is correct regardless of
 * how the per-row balance_after snapshots interleave.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundFloatService {

    private final RefundFloatLedgerRepository ledgerRepository;

    /** Below this balance, a refund still proceeds but a low-float alert is raised. */
    @Value("${payment.refund-float.low-threshold-paise:360000000}")
    private long lowThresholdPaise;

    /**
     * Debits the float to fund a refund. The float is a liability ledger, so a
     * debit is always allowed; if it would take the balance below the threshold
     * (including negative) a refund_float_low alert is raised rather than blocking
     * the traveller's money (G1 - the refund must not be withheld).
     */
    public UUID debit(long amountPaise, UUID refundId) {
        long after = ledgerRepository.currentBalancePaise() - amountPaise;
        RefundFloatLedger entry = ledgerRepository.save(RefundFloatLedger.builder()
                .direction("DEBIT")
                .amountPaise(amountPaise)
                .balanceAfter(after)
                .refundId(refundId)
                .createdAt(new Date())
                .build());
        if (after < lowThresholdPaise) {
            log.warn("refund_float_low: balance {} paise is below threshold {} paise after debiting refund {}",
                    after, lowThresholdPaise, refundId);
        }
        return entry.getId();
    }

    public UUID credit(long amountPaise) {
        long after = ledgerRepository.currentBalancePaise() + amountPaise;
        RefundFloatLedger entry = ledgerRepository.save(RefundFloatLedger.builder()
                .direction("CREDIT")
                .amountPaise(amountPaise)
                .balanceAfter(after)
                .createdAt(new Date())
                .build());
        return entry.getId();
    }

    public long balancePaise() {
        return ledgerRepository.currentBalancePaise();
    }

    public boolean isLow() {
        return balancePaise() < lowThresholdPaise;
    }

    public long lowThresholdPaise() {
        return lowThresholdPaise;
    }
}
