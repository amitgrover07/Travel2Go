package com.travel2go.backend.service;

import com.travel2go.backend.client.BookingClient;
import com.travel2go.backend.dto.AddLegRequest;
import com.travel2go.backend.dto.CreateTripRequest;
import com.travel2go.backend.dto.TripDetailResponse;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.Trip;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final LegRepository legRepository;
    private final BookingClient bookingClient;
    private final TripEventPublisher eventPublisher;

    public Trip createTrip(CreateTripRequest request, String ownerUserId) {
        Date now = new Date();
        Trip trip = Trip.builder()
                .ownerUserId(ownerUserId)
                .title(request.getTitle())
                .travellerIds(request.getTravellerIds())
                .legIds(new ArrayList<>())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status("DRAFT")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Trip saved = tripRepository.save(trip).block();

        eventPublisher.publish("trip.created", Map.of("tripId", saved.getId()));

        return saved;
    }

    public TripDetailResponse getTripDetail(String tripId, String requestingUserId) {
        Trip trip = tripRepository.findById(tripId).block();
        if (trip == null) {
            throw new IllegalArgumentException("Trip not found: " + tripId);
        }
        assertOwner(trip, requestingUserId);
        List<Leg> legs = legRepository.findByTripId(tripId).collectList().block();
        return new TripDetailResponse(trip, legs);
    }

    public Leg addLeg(String tripId, AddLegRequest request, String requestingUserId) {
        Trip trip = tripRepository.findById(tripId).block();
        if (trip == null) {
            throw new IllegalArgumentException("Trip not found: " + tripId);
        }
        assertOwner(trip, requestingUserId);

        Leg leg = Leg.builder()
                .tripId(tripId)
                .type(request.getType())
                .status("SELECTED")
                .pricePaise(request.getPricePaise())
                .quoteToken(request.getQuoteToken())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .metadata(request.getMetadata())
                .build();

        Leg savedLeg = legRepository.save(leg).block();

        trip.getLegIds().add(savedLeg.getId());
        trip.setUpdatedAt(new Date());
        tripRepository.save(trip).block();

        return savedLeg;
    }

    private void assertOwner(Trip trip, String requestingUserId) {
        if (!trip.getOwnerUserId().equals(requestingUserId)) {
            throw new AccessDeniedException("Not authorized to access trip: " + trip.getId());
        }
    }
}
