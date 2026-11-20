package com.travel2go.backend.service;

import com.travel2go.backend.client.BookingClient;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.GroupBooking;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.model.TravellerGroup;
import com.travel2go.backend.model.TravellerProfile;
import com.travel2go.backend.model.Trip;
import com.travel2go.backend.repository.GroupBookingRepository;
import com.travel2go.backend.repository.GroupRepository;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock private GroupRepository groupRepository;
    @Mock private GroupBookingRepository groupBookingRepository;
    @Mock private LegRepository legRepository;
    @Mock private TripRepository tripRepository;
    @Mock private QuoteTokenService quoteTokenService;
    @Mock private BookingClient bookingClient;
    @Mock private EntitlementChecker entitlementChecker;

    private GroupService service;

    @BeforeEach
    void setUp() {
        service = new GroupService(groupRepository, groupBookingRepository, legRepository, tripRepository,
                quoteTokenService, bookingClient, entitlementChecker);
        ReflectionTestUtils.setField(service, "baseCap", 5);
        lenient().when(groupRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(groupBookingRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private TravellerProfile profile(int i) {
        return TravellerProfile.builder().travellerId("t" + i).name("Traveller " + i).build();
    }

    private List<TravellerProfile> profiles(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(this::profile).toList();
    }

    @Test
    void createGroup_savesReusableProfiles_andAnInviteToken() {
        TravellerGroup group = service.createGroup("owner-1", "Goa Family", profiles(3));

        assertThat(group.getOwnerUserId()).isEqualTo("owner-1");
        assertThat(group.getSavedTravellerProfiles()).hasSize(3);          // reusable across trips
        assertThat(group.getTravellerIds()).containsExactly("t1", "t2", "t3");
        assertThat(group.getInviteToken()).isNotBlank();
    }

    @Test
    void baseCap_blocksMoreThanFiveWithoutFamilyPlus() {
        when(entitlementChecker.hasEntitlement("owner-1", GroupService.GROUP_ENTITLEMENT)).thenReturn(false);

        assertThatThrownBy(() -> service.createGroup("owner-1", "Big Group", profiles(6)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void familyPlus_raisesCapToNine() {
        when(entitlementChecker.hasEntitlement("owner-1", GroupService.GROUP_ENTITLEMENT)).thenReturn(true);

        TravellerGroup group = service.createGroup("owner-1", "Big Group", profiles(9));
        assertThat(group.getTravellerIds()).hasSize(9);

        assertThatThrownBy(() -> service.createGroup("owner-1", "Too Big", profiles(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bookGroup_priceLocksEachLeg_andIsolatesFailures() {
        TravellerGroup group = TravellerGroup.builder().id("g1").ownerUserId("owner-1").build();
        when(groupRepository.findById("g1")).thenReturn(Mono.just(group));

        Leg ok = Leg.builder().id("leg-1").tripId("trip-1").pricePaise(150000L).quoteToken("good").build();
        Leg bad = Leg.builder().id("leg-2").tripId("trip-1").pricePaise(200000L).quoteToken("tampered").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(ok));
        when(legRepository.findById("leg-2")).thenReturn(Mono.just(bad));
        when(tripRepository.findById("trip-1"))
                .thenReturn(Mono.just(Trip.builder().id("trip-1").ownerUserId("owner-1").build()));
        when(quoteTokenService.isValid("good", "leg-1", 150000L)).thenReturn(true);
        when(quoteTokenService.isValid("tampered", "leg-2", 200000L)).thenReturn(false);
        when(bookingClient.createLegBooking(any()))
                .thenReturn(LegBookingResponse.builder().legId("leg-1").status("PENDING").build());
        when(legRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        GroupBooking result = service.bookGroup("g1", "owner-1", "trip-1", List.of("leg-1", "leg-2"));

        assertThat(result.getLegResults()).hasSize(2);
        assertThat(result.getLegResults().get(0).getStatus()).isEqualTo("PENDING");
        assertThat(result.getLegResults().get(1).getStatus()).isEqualTo("FAILED"); // bad token, not booked
        // Only the price-locked leg was actually booked (G2); the other never reached the booking client.
        verify(bookingClient, times(1)).createLegBooking(any());
    }

    @Test
    void bookGroup_rejectsNonOrganiser() {
        TravellerGroup group = TravellerGroup.builder().id("g1").ownerUserId("owner-1").build();
        when(groupRepository.findById("g1")).thenReturn(Mono.just(group));

        assertThatThrownBy(() -> service.bookGroup("g1", "intruder", "trip-1", List.of("leg-1")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(bookingClient, never()).createLegBooking(any());
    }

    @Test
    void join_addsMemberViaInviteLink_asAcquisition() {
        TravellerGroup group = TravellerGroup.builder()
                .id("g1").ownerUserId("owner-1").inviteToken("tok123")
                .travellerIds(new java.util.ArrayList<>(List.of("t1")))
                .savedTravellerProfiles(new java.util.ArrayList<>(List.of(profile(1))))
                .build();
        when(groupRepository.findByInviteToken("tok123")).thenReturn(Mono.just(group));

        GroupService.JoinResult joined = service.join("tok123", "joiner-1", profile(2));

        // Minimal, PII-free response (no other members' profiles / id numbers, no invite token).
        assertThat(joined.groupId()).isEqualTo("g1");
        assertThat(joined.joinedTravellerId()).isEqualTo("t2");
        assertThat(joined.memberCount()).isEqualTo(2);
        // The member was still added to the stored group.
        assertThat(group.getTravellerIds()).contains("t1", "t2");
    }

    @Test
    void bookGroup_rejectsLegsOnAnotherUsersTrip() {
        TravellerGroup group = TravellerGroup.builder().id("g1").ownerUserId("owner-1").build();
        when(groupRepository.findById("g1")).thenReturn(Mono.just(group));
        Leg leg = Leg.builder().id("leg-x").tripId("trip-victim").pricePaise(150000L).quoteToken("good").build();
        when(legRepository.findById("leg-x")).thenReturn(Mono.just(leg));
        when(tripRepository.findById("trip-victim"))
                .thenReturn(Mono.just(Trip.builder().id("trip-victim").ownerUserId("someone-else").build()));

        GroupBooking result = service.bookGroup("g1", "owner-1", "trip-victim", List.of("leg-x"));

        assertThat(result.getLegResults().get(0).getStatus()).isEqualTo("FAILED");
        verify(bookingClient, never()).createLegBooking(any());
    }

    @Test
    void join_rejectsUnknownToken() {
        when(groupRepository.findByInviteToken("nope")).thenReturn(Mono.empty());
        assertThatThrownBy(() -> service.join("nope", "joiner-1", profile(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
