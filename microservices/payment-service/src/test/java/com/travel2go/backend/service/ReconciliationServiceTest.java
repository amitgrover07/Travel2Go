package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private SettlementRepository settlementRepository;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(paymentRepository, refundRepository, settlementRepository);
    }

    @Test
    void getSummary_aggregatesTotalsAndUnsettledCaptures() {
        Payment unsettled = Payment.builder().bookingRef("leg-1").status("CAPTURED").amountPaise(150000L).build();
        when(paymentRepository.sumCapturedAmountPaise()).thenReturn(500000L);
        when(refundRepository.sumRefundedAmountPaise()).thenReturn(150000L);
        when(settlementRepository.sumSettledAmountPaise()).thenReturn(350000L);
        when(paymentRepository.findCapturedWithoutSettlement()).thenReturn(List.of(unsettled));

        ReconciliationSummary summary = reconciliationService.getSummary();

        assertThat(summary.totalCapturedPaise()).isEqualTo(500000L);
        assertThat(summary.totalRefundedPaise()).isEqualTo(150000L);
        assertThat(summary.totalSettledPaise()).isEqualTo(350000L);
        assertThat(summary.unsettledCaptures()).containsExactly(unsettled);
    }
}
