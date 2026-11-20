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

/**
 * One movement on the ring-fenced refund float. The authoritative balance is the
 * signed SUM over all rows (CREDIT +, DEBIT -); {@code balanceAfter} is a snapshot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refund_float_ledger")
public class RefundFloatLedger {
    @Id
    @GeneratedValue
    private UUID id;

    private String direction; // DEBIT | CREDIT

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "refund_id")
    private UUID refundId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
