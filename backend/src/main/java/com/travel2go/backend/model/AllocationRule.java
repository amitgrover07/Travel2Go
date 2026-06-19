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
@Document(collectionName = "allocation_rules")
public class AllocationRule {
    
    @DocumentId
    private String id;
    
    private String ruleCode;
    private String title;
    private String description;
    private String status; // ACTIVE, INACTIVE
    
    // Pricing configuration
    private double baseRoomRate;      // per room per night
    private double extraAdultRate;     // per extra adult per night
    private double cwbRate;            // Child With Bed per night
    private double cnbRate;            // Child No Bed per night
    private double sightseeingTicketPrice; // per headcount

    @Builder.Default
    private String packageType = "FIT";        // GIT, FIT
    @Builder.Default
    private String mealPlan = "CP";           // CP, MAP, AP
    @Builder.Default
    private boolean includeSightseeing = true; // whether sightseeing is included in base calculation
    
    // Vehicles configuration
    private List<VehicleRate> vehicles;
    
    // Package mapping: list of package IDs this rule applies to
    private List<String> mappedPackageIds;
    
    private List<CustomActivity> customActivities;
    
    private HolidayPackage.Audit audit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VehicleRate {
        private String vehicleName; // e.g. Sedan, Innova Crysta, SUV, Tempo Traveller
        private int maxPax;         // e.g. 4 for Sedan, 6 for Innova, 12 for Tempo Traveller
        private double dailyRate;
        private double tollCharges;
        private double permitTax;
        private double driverAllowance; // per day
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomActivity {
        private String categoryName;
        private String optionName;
        private double basePrice;
        private double markupPercentage;
        private double price;
    }
}
