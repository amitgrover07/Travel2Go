package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    private String status; // provider outcome: PENDING | SUCCESS | FAILED

    // --- F3 Refund SLA engine -------------------------------------------------
    // The SLA lifecycle is tracked separately from the provider outcome above:
    // `status` is "did the provider accept the refund"; `slaStatus` is "have we
    // met the < 72h settlement promise". Keeping them apart preserves the existing
    // SUCCESS-based reconciliation query while adding the trust-SLA tracking.
    @Column(name = "sla_status")
    private String slaStatus; // INITIATED | SETTLED | BREACHED

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sla_due_at")
    private Date slaDueAt;

    @Column(name = "trigger")
    private String trigger; // USER_CANCEL | DISRUPTION | MANUAL

    @Column(name = "float_txn_id")
    private UUID floatTxnId; // the refund_float_ledger DEBIT that funded this refund

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
