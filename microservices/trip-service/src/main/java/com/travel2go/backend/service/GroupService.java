package com.travel2go.backend.service;

import com.travel2go.backend.client.BookingClient;
import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.GroupBooking;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.TravellerGroup;
import com.travel2go.backend.model.TravellerProfile;
import com.travel2go.backend.repository.GroupBookingRepository;
import com.travel2go.backend.repository.GroupRepository;
import com.travel2go.backend.repository.LegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Group travel (F8): an organiser builds a group of reusable traveller profiles and
 * books for all members in one flow. Each member leg is price-locked (G2) and
 * payment-gated individually; partial outcomes are allowed and reported per leg, so
 * one member's failure never silently confirms the rest. Saved profiles and the group
 * are durable and reusable across trips (the trust/workflow lock-in moat). An invite
 * link lets a new member join and records an acquisition signal (growth loop).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    /** Family+ entitlement that raises the group member cap. */
    public static final String GROUP_ENTITLEMENT = "GROUP_UP_TO_9";
    private static final int FAMILY_PLUS_CAP = 9;

    private final GroupRepository groupRepository;
    private final GroupBookingRepository groupBookingRepository;
    private final LegRepository legRepository;
    private final QuoteTokenService quoteTokenService;
    private final BookingClient bookingClient;
    private final EntitlementChecker entitlementChecker;

    @Value("${group.base-cap:5}")
    private int baseCap;

    public TravellerGroup createGroup(String ownerUserId, String name, List<TravellerProfile> profiles) {
        List<TravellerProfile> members = profiles == null ? new ArrayList<>() : new ArrayList<>(profiles);
        enforceCap(ownerUserId, members.size());

        TravellerGroup group = TravellerGroup.builder()
                .ownerUserId(ownerUserId)
                .name(name)
                .travellerIds(travellerIds(members))
                .savedTravellerProfiles(members)
                .inviteToken(UUID.randomUUID().toString().replace("-", ""))
                .createdAt(new Date())
                .build();
        return groupRepository.save(group).block();
    }

    public TravellerGroup getGroup(String groupId, String requestingUserId) {
        TravellerGroup group = load(groupId);
        assertOrganiser(group, requestingUserId);
        return group;
    }

    public TravellerGroup addTraveller(String groupId, String ownerUserId, TravellerProfile profile) {
        TravellerGroup group = load(groupId);
        assertOrganiser(group, ownerUserId);
        addMember(group, profile, ownerUserId);
        return groupRepository.save(group).block();
    }

    public String createInvite(String groupId, String ownerUserId) {
        TravellerGroup group = load(groupId);
        assertOrganiser(group, ownerUserId);
        if (group.getInviteToken() == null) {
            group.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
            groupRepository.save(group).block();
        }
        return group.getInviteToken();
    }

    /** A new member joins via the invite link; recorded as an acquisition signal. */
    public TravellerGroup join(String inviteToken, TravellerProfile profile) {
        TravellerGroup group = groupRepository.findByInviteToken(inviteToken).block();
        if (group == null) {
            throw new IllegalArgumentException("Invalid invite token");
        }
        addMember(group, profile, group.getOwnerUserId());
        TravellerGroup saved = groupRepository.save(group).block();
        log.info("group.member_joined group={} traveller={} (acquisition)", group.getId(), profile.getTravellerId());
        return saved;
    }

    /**
     * Books each member leg in one flow. Each leg is price-locked (G2) and payment-
     * gated via the existing per-leg path; failures are isolated and reported per leg.
     */
    public GroupBooking bookGroup(String groupId, String ownerUserId, String tripId, List<String> legIds) {
        TravellerGroup group = load(groupId);
        assertOrganiser(group, ownerUserId);

        List<GroupBooking.LegResult> results = new ArrayList<>();
        for (String legId : legIds) {
            results.add(bookOneLeg(tripId, legId));
        }

        GroupBooking groupBooking = GroupBooking.builder()
                .groupId(groupId)
                .tripId(tripId)
                .legResults(results)
                .createdAt(new Date())
                .build();
        return groupBookingRepository.save(groupBooking).block();
    }

    private GroupBooking.LegResult bookOneLeg(String tripId, String legId) {
        try {
            Leg leg = legRepository.findById(legId).block();
            if (leg == null || !tripId.equals(leg.getTripId())) {
                return result(legId, "FAILED", "leg not found for trip");
            }
            Long price = leg.getPricePaise();
            String token = leg.getQuoteToken();
            if (price == null || token == null || !quoteTokenService.isValid(token, legId, price)) {
                return result(legId, "FAILED", "quote token invalid or expired"); // G2
            }
            LegBookingResponse response = bookingClient.createLegBooking(LegBookingRequest.builder()
                    .tripId(tripId).legId(legId).quoteToken(token).amountPaise(price).build());
            leg.setStatus("PENDING");
            leg.setSupplierRef(response.getLegId());
            legRepository.save(leg).block();
            return result(legId, "PENDING", null);
        } catch (RuntimeException e) {
            // Isolate the failure: it must not confirm or affect the other members' legs.
            log.warn("Group leg booking failed for leg {}: {}", legId, e.getMessage());
            return result(legId, "FAILED", e.getMessage());
        }
    }

    private void addMember(TravellerGroup group, TravellerProfile profile, String capUserId) {
        if (group.getSavedTravellerProfiles() == null) {
            group.setSavedTravellerProfiles(new ArrayList<>());
        }
        if (group.getTravellerIds() == null) {
            group.setTravellerIds(new ArrayList<>());
        }
        if (group.getTravellerIds().contains(profile.getTravellerId())) {
            return; // idempotent add
        }
        enforceCap(capUserId, group.getTravellerIds().size() + 1);
        group.getSavedTravellerProfiles().add(profile);
        group.getTravellerIds().add(profile.getTravellerId());
    }

    private void enforceCap(String ownerUserId, int size) {
        int cap = entitlementChecker.hasEntitlement(ownerUserId, GROUP_ENTITLEMENT) ? FAMILY_PLUS_CAP : baseCap;
        if (size > cap) {
            throw new IllegalArgumentException("Group exceeds member cap of " + cap);
        }
    }

    private List<String> travellerIds(List<TravellerProfile> profiles) {
        List<String> ids = new ArrayList<>();
        for (TravellerProfile p : profiles) {
            ids.add(p.getTravellerId());
        }
        return ids;
    }

    private TravellerGroup load(String groupId) {
        TravellerGroup group = groupRepository.findById(groupId).block();
        if (group == null) {
            throw new IllegalArgumentException("Group not found: " + groupId);
        }
        return group;
    }

    private void assertOrganiser(TravellerGroup group, String requestingUserId) {
        if (!group.getOwnerUserId().equals(requestingUserId)) {
            throw new AccessDeniedException("Not the organiser of group " + group.getId());
        }
    }

    private GroupBooking.LegResult result(String legId, String status, String detail) {
        return GroupBooking.LegResult.builder().legId(legId).status(status).detail(detail).build();
    }
}
