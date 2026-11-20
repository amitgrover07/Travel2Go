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
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    private String status; // ACTIVE | PAST_DUE | CANCELLED | EXPIRED

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "current_period_end", nullable = false)
    private Date currentPeriodEnd;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "grace_until")
    private Date graceUntil;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "period_index", nullable = false)
    @Builder.Default
    private Integer periodIndex = 1;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private boolean autoRenew = true;

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
