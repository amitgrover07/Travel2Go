package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "custom_packages")
public class CustomPackage {
    
    @DocumentId
    private String id;
    
    private String packageCode;
    private String title;
    private String destination;
    private String status;
    private String packageType;
    private String overview;
    private String specialNotes;
    
    private HolidayPackage.Duration duration;
    private HolidayPackage.Pricing pricing;
    private HolidayPackage.Media media;
    
    private List<String> inclusions;
    private List<String> exclusions;
    private List<HolidayPackage.ItineraryDay> itinerary;
    
    private HolidayPackage.Audit audit;
}
