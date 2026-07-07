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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private LegRepository legRepository;
    @Mock private BookingClient bookingClient;
    @Mock private TripEventPublisher eventPublisher;
    @Mock private QuoteTokenService quoteTokenService;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, legRepository, bookingClient, eventPublisher, quoteTokenService);
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

        Trip result = tripService.createTrip(request, "user-1");

        assertThat(result.getId()).isEqualTo("trip-1");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
        verify(eventPublisher).publish(eq("trip.created"), any());
    }

    @Test
    void getTripDetail_returnsTripWithItsLegs() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").title("Goa Family Trip").build();
        Leg leg = Leg.builder().id("leg-1").tripId("trip-1").type("RAIL").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.findByTripId("trip-1")).thenReturn(Flux.just(leg));

        TripDetailResponse result = tripService.getTripDetail("trip-1", "user-1");

        assertThat(result.getTrip().getId()).isEqualTo("trip-1");
        assertThat(result.getLegs()).hasSize(1);
        assertThat(result.getLegs().get(0).getId()).isEqualTo("leg-1");
    }

    @Test
    void getTripDetail_throwsWhenTripMissing() {
        when(tripRepository.findById("missing")).thenReturn(Mono.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tripService.getTripDetail("missing", "user-1"));
    }

    @Test
    void getTripDetail_rejectsNonOwner() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").build();
        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> tripService.getTripDetail("trip-1", "user-2"));
    }

    @Test
    void addLeg_appendsLegIdToTripAndSavesLeg() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").legIds(new java.util.ArrayList<>()).build();
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

        Leg result = tripService.addLeg("trip-1", request, "user-1");

        assertThat(result.getId()).isEqualTo("leg-1");
        assertThat(result.getStatus()).isEqualTo("SELECTED");
        assertThat(trip.getLegIds()).containsExactly("leg-1");
    }

    @Test
    void addLeg_rejectsNonOwner_withoutSavingLeg() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").legIds(new java.util.ArrayList<>()).build();
        AddLegRequest request = AddLegRequest.builder().type("RAIL").pricePaise(150000L).build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> tripService.addLeg("trip-1", request, "user-2"));

        verify(legRepository, never()).save(any());
    }

    @Test
    void bookLeg_confirmsLegAndPublishesEvent() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").build();
        com.travel2go.backend.model.Leg leg = com.travel2go.backend.model.Leg.builder()
                .id("leg-1").tripId("trip-1").status("SELECTED")
                .pricePaise(150000L).quoteToken("quote-token-abc").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(quoteTokenService.isValid("quote-token-abc", "leg-1", 150000L)).thenReturn(true);
        when(bookingClient.createLegBooking(any())).thenReturn(
                com.travel2go.backend.dto.LegBookingResponse.builder()
                        .bookingId("booking-99").status("CONFIRMED").build());
        when(legRepository.save(any(com.travel2go.backend.model.Leg.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        com.travel2go.backend.model.Leg result = tripService.bookLeg("trip-1", "leg-1", "user-1");

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getSupplierRef()).isEqualTo("booking-99");
        verify(eventPublisher).publish(eq("leg.booked"), any());

        org.mockito.ArgumentCaptor<com.travel2go.backend.dto.LegBookingRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.travel2go.backend.dto.LegBookingRequest.class);
        verify(bookingClient).createLegBooking(captor.capture());
        assertThat(captor.getValue().getAmountPaise()).isEqualTo(150000L);
        assertThat(captor.getValue().getQuoteToken()).isEqualTo("quote-token-abc");
    }

    @Test
    void bookLeg_rejectsLegFromDifferentTrip() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").build();
        com.travel2go.backend.model.Leg leg = com.travel2go.backend.model.Leg.builder()
                .id("leg-1").tripId("trip-OTHER").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tripService.bookLeg("trip-1", "leg-1", "user-1"));

        verify(bookingClient, never()).createLegBooking(any());
    }

    @Test
    void bookLeg_rejectsNonOwner_withoutCallingBookingClient() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> tripService.bookLeg("trip-1", "leg-1", "user-2"));

        verify(bookingClient, never()).createLegBooking(any());
        verify(quoteTokenService, never()).isValid(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void bookLeg_rejectsInvalidQuoteToken_withoutCallingBookingClient() {
        Trip trip = Trip.builder().id("trip-1").ownerUserId("user-1").build();
        com.travel2go.backend.model.Leg leg = com.travel2go.backend.model.Leg.builder()
                .id("leg-1").tripId("trip-1").status("SELECTED")
                .pricePaise(150000L).quoteToken("tampered-token").build();

        when(tripRepository.findById("trip-1")).thenReturn(Mono.just(trip));
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(quoteTokenService.isValid("tampered-token", "leg-1", 150000L)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> tripService.bookLeg("trip-1", "leg-1", "user-1"));

        verify(bookingClient, never()).createLegBooking(any());
    }
}
