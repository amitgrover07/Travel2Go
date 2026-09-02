package com.travel2go.backend.service;

import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegBookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private QuoteTokenService quoteTokenService;

    private LegBookingService legBookingService;

    @BeforeEach
    void setUp() {
        legBookingService = new LegBookingService(bookingRepository, quoteTokenService);
        lenient().when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void createLegBooking_validTokenPersistsPendingWithOwner() {
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.empty());
        when(quoteTokenService.isValid("quote-abc", "leg-1", 150000L)).thenReturn(true);

        Booking result = legBookingService.createLegBooking("trip-1", "leg-1", "quote-abc", 150000L, "user-1");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
        assertThat(result.getLegId()).isEqualTo("leg-1");
    }

    @Test
    void createLegBooking_invalidTokenRejectsAndPersistsAuditRecord() {
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.empty());
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        assertThatThrownBy(() ->
                legBookingService.createLegBooking("trip-1", "leg-1", "bad-token", 150000L, "user-1"))
                .isInstanceOf(LegBookingRejectedException.class);

        verify(bookingRepository).save(argThatStatusIs("REJECTED"));
    }

    @Test
    void createLegBooking_existingBookingForLegIdIsIdempotent() {
        Booking existing = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(existing));

        Booking result = legBookingService.createLegBooking("trip-1", "leg-1", "quote-abc", 150000L, "user-1");

        assertThat(result).isSameAs(existing);
        verify(quoteTokenService, never()).isValid(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void getBooking_returnsForOwner() {
        Booking booking = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(booking));

        Booking result = legBookingService.getBooking("leg-1", "user-1", false);

        assertThat(result).isSameAs(booking);
    }

    @Test
    void getBooking_throwsForNonOwnerNonAdmin() {
        Booking booking = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(booking));

        assertThatThrownBy(() -> legBookingService.getBooking("leg-1", "user-2", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getBooking_allowsAdminForAnyOwner() {
        Booking booking = Booking.builder().id("b1").legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(booking));

        Booking result = legBookingService.getBooking("leg-1", "admin-user", true);

        assertThat(result).isSameAs(booking);
    }

    private static Booking argThatStatusIs(String status) {
        return org.mockito.ArgumentMatchers.argThat(b -> status.equals(b.getStatus()));
    }
}
