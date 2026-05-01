package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "packages")
public class HolidayPackage {
    
    @Id
    private String id;
    
    @Version
    private Long version;
    
    private String packageCode;
    private String title;
    private String destination;
    private String status; // ACTIVE, INACTIVE
    private String overview;

    private Duration duration;
    private Pricing pricing;
    private Media media;

    private List<String> inclusions;
    private List<String> exclusions;
    private List<ItineraryItem> itinerary;

    private Audit audit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Duration {
        private Integer days;
        private Integer nights;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Pricing {
        private String currency;
        private Double basePrice;
        private Double discountPercentage;
        private Double finalPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Media {
        private String thumbnailUrl;
        private List<String> galleryUrls;
        private String altText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItineraryItem {
        private Integer day;
        private String title;
        private String activities;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Audit {
        @CreatedBy
        private String createdBy;

        @CreatedDate
        private Instant createdAt;

        @LastModifiedBy
        private String updatedBy;

        @LastModifiedDate
        private Instant updatedAt;
    }
}
