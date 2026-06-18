package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "travel_configurations")
public class TravelConfiguration {
    
    @DocumentId
    private String id;
    
    private String configCode;
    private String title;
    private String description;
    private String status;
    
    private String targetType; // e.g. DESTINATION, PACKAGE, NONE
    private String targetValue; // e.g. "Kerala" or packageId
    
    private List<ConfigOption> options;
    
    private HolidayPackage.Audit audit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfigOption {
        private String categoryName;
        private String optionName;
        private double price;
        private double basePrice;
        private double markupPercentage;
    }
}
