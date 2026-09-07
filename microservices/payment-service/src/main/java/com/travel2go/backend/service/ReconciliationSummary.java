package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;

import java.util.List;

public record ReconciliationSummary(
        long totalCapturedPaise,
        long totalRefundedPaise,
        long totalSettledPaise,
        List<Payment> unsettledCaptures) {
}
