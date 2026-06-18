package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String location;
    private String packageId;
    private String packageTitle;
    private Integer adults;
    private Integer children;
    private Double basePrice;
    private Double discountPercentage;
    private Double finalPrice;
}
