package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
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
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "booking_ref", nullable = false)
    private String bookingRef;

    private String method; // UPI | CARD | NETBANKING
    private String status; // CREATED | CAPTURED | FAILED | REJECTED | REFUNDED

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "fee_paise", nullable = false)
    @Builder.Default
    private Long feePaise = 0L; // MUST always be 0 (G1)

    @Column(name = "provider_order_id")
    private String providerRef; // provider ORDER id (set at CREATED)

    @Column(name = "provider_payment_id")
    private String providerPaymentId; // provider PAYMENT id (set on CAPTURED, from the webhook)

    @Column(name = "quote_token_validated")
    private Boolean quoteTokenValidated;

    @Column(name = "owner_user_id")
    private String ownerUserId; // captured from the JWT at createOrder time (P1.2)

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = new Date();
    }
}
