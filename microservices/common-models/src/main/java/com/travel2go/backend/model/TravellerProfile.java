package com.travel2go.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A reusable traveller profile saved on a group (F8) so a family never re-enters
 * the same names/IDs across trips - part of the stated trust/workflow lock-in moat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravellerProfile {
    private String travellerId; // stable id reused across trips
    private String name;
    private String idType;      // AADHAAR | PASSPORT | ...
    private String idNumber;
}
