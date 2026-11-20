package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "coupons")
public class Coupon {
    @Id
    private String code;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(nullable = false)
    private boolean active;
}
