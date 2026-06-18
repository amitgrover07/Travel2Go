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
    private boolean isCustom;
    private String bestTimeToReach;
    private String leadId; // Optional: If sending from an existing lead
    private Integer adults;
    private Integer children;
    private Double basePrice;
    private Double discountPercentage;
    private Double finalPrice;

    // Manual getters and setter to ensure Jackson maps JSON "isCustom" property correctly
    public boolean getIsCustom() {
        return this.isCustom;
    }

    public boolean isCustom() {
        return this.isCustom;
    }
    
    public void setIsCustom(boolean isCustom) {
        this.isCustom = isCustom;
    }
}
