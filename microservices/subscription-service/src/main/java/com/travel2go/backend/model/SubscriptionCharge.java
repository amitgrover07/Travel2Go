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
@Table(name = "subscription_charges")
public class SubscriptionCharge {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    private String status; // PENDING | SUCCESS | FAILED

    @Column(name = "provider_charge_id")
    private String providerChargeId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
