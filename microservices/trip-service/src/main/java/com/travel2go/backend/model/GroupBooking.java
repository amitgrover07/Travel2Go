package com.travel2go.backend.model;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Links a group to the per-member leg bookings made in one organiser flow (F8).
 * Partial outcomes are allowed and each leg's result is recorded independently -
 * one member's failure never silently confirms the rest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collectionName = "group_bookings")
public class GroupBooking {
    @DocumentId
    private String id;

    private String groupId;
    private String tripId;
    private List<LegResult> legResults;
    private Date createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LegResult {
        private String legId;
        private String status; // PENDING | FAILED
        private String detail;
    }
}
