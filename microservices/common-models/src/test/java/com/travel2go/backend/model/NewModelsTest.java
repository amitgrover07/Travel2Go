package com.travel2go.backend.model;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewModelsTest {

    @Test
    void tripBuilderRoundTrips() {
        Date now = new Date();
        Trip trip = Trip.builder()
                .id("trip-1")
                .ownerUserId("user-1")
                .title("Goa Family Trip")
                .status("DRAFT")
                .travellerIds(List.of("trav-1", "trav-2"))
                .legIds(List.of())
                .origin("Jhansi")
                .destination("Goa")
                .startDate(now)
                .endDate(now)
                .budgetPaise(5000000L)
                .savingsLedgerPaise(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(trip.getId()).isEqualTo("trip-1");
        assertThat(trip.getStatus()).isEqualTo("DRAFT");
        assertThat(trip.getTravellerIds()).containsExactly("trav-1", "trav-2");
    }

    @Test
    void legBuilderRoundTrips() {
        Leg leg = Leg.builder()
                .id("leg-1")
                .tripId("trip-1")
                .type("RAIL")
                .status("SELECTED")
                .pricePaise(150000L)
                .quoteToken("token-abc")
                .metadata(Map.of("trainNumber", "12345"))
                .build();

        assertThat(leg.getTripId()).isEqualTo("trip-1");
        assertThat(leg.getType()).isEqualTo("RAIL");
        assertThat(leg.getMetadata()).containsEntry("trainNumber", "12345");
    }

    @Test
    void travellerBuilderRoundTrips() {
        Traveller traveller = Traveller.builder()
                .id("trav-1")
                .ownerUserId("user-1")
                .fullName("Rajesh Kumar")
                .idType("AADHAAR")
                .idReferenceToken("tok-ref-001")
                .relationship("SELF")
                .isMinor(false)
                .build();

        assertThat(traveller.getFullName()).isEqualTo("Rajesh Kumar");
        assertThat(traveller.getIdReferenceToken()).isEqualTo("tok-ref-001");
        assertThat(traveller.getIsMinor()).isFalse();
    }

    @Test
    void travellerGroupBuilderRoundTrips() {
        TravellerGroup group = TravellerGroup.builder()
                .id("group-1")
                .ownerUserId("user-1")
                .name("Kumar Family")
                .travellerIds(List.of("trav-1", "trav-2", "trav-3"))
                .build();

        assertThat(group.getTravellerIds()).hasSize(3);
    }
}
