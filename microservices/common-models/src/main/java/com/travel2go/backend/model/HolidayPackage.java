package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "packages")
public class HolidayPackage {
    
    @DocumentId
    private String id;
    
    private String packageCode;
    private String title;
    private String destination;
    private String status;
    private String packageType;
    private String overview;
    private String specialNotes;
    
    private Duration duration;
    private Pricing pricing;
    private Media media;
    
    private List<String> inclusions;
    private List<String> exclusions;
    private List<ItineraryDay> itinerary;
    
    private Audit audit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Duration {
        private int days;
        private int nights;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pricing {
        private String currency;
        private double basePrice;
        private double discountPercentage;
        private double finalPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Media {
        private String thumbnailUrl;
        private List<String> galleryUrls;
        private String altText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItineraryDay {
        private int day;
        private String title;
        private String activities;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Audit {
        private String createdBy;
        private Instant createdAt;
        private String updatedBy;
        private Instant updatedAt;
    }
}
