package com.travel2go.backend.controller;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegBookingControllerTest {

    @Mock private BookingRepository bookingRepository;

    private LegBookingController controller;

    @BeforeEach
    void setUp() {
        controller = new LegBookingController(bookingRepository);
    }

    @Test
    void createLegBooking_alwaysSetsZeroFee() {
        LegBookingRequest request = LegBookingRequest.builder()
                .tripId("trip-1")
                .legId("leg-1")
                .quoteToken("quote-token-abc")
                .amountPaise(150000L)
                .build();

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId("booking-1");
            return Mono.just(b);
        });

        ResponseEntity<LegBookingResponse> response = controller.createLegBooking(request);

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        Booking savedBooking = bookingCaptor.getValue();

        assertThat(savedBooking.getFeePaise()).isEqualTo(0L);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBookingId()).isEqualTo("booking-1");
    }

    @Test
    void createLegBooking_setsStatusConfirmedAndPersistsRequestFields() {
        LegBookingRequest request = LegBookingRequest.builder()
                .tripId("trip-2")
                .legId("leg-2")
                .quoteToken("quote-xyz")
                .amountPaise(200000L)
                .build();

        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId("booking-2");
            return Mono.just(b);
        });

        ResponseEntity<LegBookingResponse> response = controller.createLegBooking(request);

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        Booking savedBooking = bookingCaptor.getValue();

        assertThat(savedBooking.getTripId()).isEqualTo("trip-2");
        assertThat(savedBooking.getLegId()).isEqualTo("leg-2");
        assertThat(savedBooking.getQuoteToken()).isEqualTo("quote-xyz");
        assertThat(savedBooking.getAmountPaise()).isEqualTo(200000L);
        assertThat(savedBooking.getStatus()).isEqualTo("CONFIRMED");

        assertThat(response.getBody().getStatus()).isEqualTo("CONFIRMED");
    }
}
