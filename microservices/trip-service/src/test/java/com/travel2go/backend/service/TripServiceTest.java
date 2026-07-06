package com.travel2go.backend.service;

import com.travel2go.backend.client.BookingClient;
import com.travel2go.backend.dto.AddLegRequest;
import com.travel2go.backend.dto.CreateTripRequest;
import com.travel2go.backend.dto.TripDetailResponse;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private LegRepository legRepository;
    @Mock private BookingClient bookingClient;
    @Mock private TripEventPublisher eventPublisher;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, legRepository, bookingClient, eventPublisher);
    }

    @Test
    void createTrip_savesDraftTripAndPublishesEvent() {
        CreateTripRequest request = CreateTripRequest.builder()
                .title("Goa Family Trip")
                .travellerIds(List.of("trav-1", "trav-2"))
                .origin("Jhansi")
                .destination("Goa")
                .build();

        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId("trip-1");
            return Mono.just(t);
        });

        Trip result = tripService.createTrip(request);

        assertThat(result.getId()).isEqualTo("trip-1");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(eventPublisher).publish(eq("trip.created"), any());
    }

    @Test
    void getTripDetail_returnsTripWithItsLegs() {
        Trip trip = Trip.builder().id("trip-1").title("Goa Family Trip").build();
        Leg leg = Leg.builder().id("leg-1").tripId("trip-1").type("RAIL").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.findByTripId("trip-1")).thenReturn(Flux.just(leg));

        TripDetailResponse result = tripService.getTripDetail("trip-1");

        assertThat(result.getTrip().getId()).isEqualTo("trip-1");
        assertThat(result.getLegs()).hasSize(1);
        assertThat(result.getLegs().get(0).getId()).isEqualTo("leg-1");
    }

    @Test
    void getTripDetail_throwsWhenTripMissing() {
        when(tripRepository.findById("missing")).thenReturn(Mono.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tripService.getTripDetail("missing"));
    }

    @Test
    void addLeg_appendsLegIdToTripAndSavesLeg() {
        Trip trip = Trip.builder().id("trip-1").legIds(new java.util.ArrayList<>()).build();
        AddLegRequest request = AddLegRequest.builder()
                .type("RAIL")
                .pricePaise(150000L)
                .quoteToken("quote-abc")
                .build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.save(any(Leg.class))).thenAnswer(inv -> {
            Leg l = inv.getArgument(0);
            l.setId("leg-1");
            return Mono.just(l);
        });
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        Leg result = tripService.addLeg("trip-1", request);

        assertThat(result.getId()).isEqualTo("leg-1");
        assertThat(result.getStatus()).isEqualTo("SELECTED");
        assertThat(trip.getLegIds()).containsExactly("leg-1");
    }
}
