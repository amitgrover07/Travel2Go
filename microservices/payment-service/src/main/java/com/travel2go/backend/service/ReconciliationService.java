package com.travel2go.backend.service;

import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final SettlementRepository settlementRepository;

    public ReconciliationSummary getSummary() {
        return new ReconciliationSummary(
                paymentRepository.sumCapturedAmountPaise(),
                refundRepository.sumRefundedAmountPaise(),
                settlementRepository.sumSettledAmountPaise(),
                paymentRepository.findCapturedWithoutSettlement());
    }
}
