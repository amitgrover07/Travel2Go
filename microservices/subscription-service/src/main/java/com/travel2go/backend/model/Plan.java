package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "plans")
public class Plan {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;   // FAMILY_PLUS | BUSINESS

    private String name;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(name = "period_days", nullable = false)
    private Integer periodDays;

    @Column(nullable = false)
    private String entitlements; // comma-separated keys

    public List<String> entitlementKeys() {
        if (entitlements == null || entitlements.isBlank()) {
            return List.of();
        }
        return Arrays.stream(entitlements.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
