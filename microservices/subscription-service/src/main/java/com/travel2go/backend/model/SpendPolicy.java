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
@Table(name = "spend_policies")
public class SpendPolicy {
    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "per_trip_cap_paise", nullable = false)
    private Long perTripCapPaise;

    @Column(name = "monthly_cap_paise", nullable = false)
    private Long monthlyCapPaise;
}
