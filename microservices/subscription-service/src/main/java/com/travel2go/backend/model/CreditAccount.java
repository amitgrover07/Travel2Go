package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "credit_accounts")
public class CreditAccount {
    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "limit_paise", nullable = false)
    @Builder.Default
    private Long limitPaise = 0L;

    @Column(name = "outstanding_paise", nullable = false)
    @Builder.Default
    private Long outstandingPaise = 0L;

    public long availablePaise() {
        return limitPaise - outstandingPaise;
    }
}
