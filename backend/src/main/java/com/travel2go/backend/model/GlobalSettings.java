package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "settings")
public class GlobalSettings {
    @DocumentId
    private String id; // Use "global" as id
    private String termsAndConditions;
    private Double childPriceFactor;
    private Double extraRoomSurcharge;
    private Double groupDiscountRate;
    private Double maxGroupDiscount;

    public Double getChildPriceFactor() {
        return childPriceFactor != null ? childPriceFactor : 0.7;
    }
    public Double getExtraRoomSurcharge() {
        return extraRoomSurcharge != null ? extraRoomSurcharge : 1500.0;
    }
    public Double getGroupDiscountRate() {
        return groupDiscountRate != null ? groupDiscountRate : 0.01;
    }
    public Double getMaxGroupDiscount() {
        return maxGroupDiscount != null ? maxGroupDiscount : 0.10;
    }
}
