package com.travel2go.backend.controller;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.service.LegBookingRejectedException;
import com.travel2go.backend.service.LegBookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegBookingControllerTest {

    @Mock private LegBookingService legBookingService;

    private LegBookingController controller;

    @BeforeEach
    void setUp() {
        controller = new LegBookingController(legBookingService);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("user-1", null, java.util.List.of()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void createLegBooking_returnsPendingWithLegIdReference() {
        LegBookingRequest request = LegBookingRequest.builder()
                .tripId("trip-1").legId("leg-1").quoteToken("quote-abc").amountPaise(150000L).build();

        Booking pending = Booking.builder().legId("leg-1").status("PENDING").ownerUserId("user-1").build();
        when(legBookingService.createLegBooking("trip-1", "leg-1", "quote-abc", 150000L, "user-1"))
                .thenReturn(pending);

        ResponseEntity<LegBookingResponse> response = controller.createLegBooking(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getLegId()).isEqualTo("leg-1");
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createLegBooking_rejectedTokenReturns402() {
        LegBookingRequest request = LegBookingRequest.builder()
                .tripId("trip-1").legId("leg-1").quoteToken("bad").amountPaise(150000L).build();

        when(legBookingService.createLegBooking(eq("trip-1"), eq("leg-1"), eq("bad"), anyLong(), eq("user-1")))
                .thenThrow(new LegBookingRejectedException("invalid"));

        ResponseEntity<LegBookingResponse> response = controller.createLegBooking(request);

        assertThat(response.getStatusCode().value()).isEqualTo(402);
    }

    @Test
    void getBooking_returns404WhenServiceRejects() {
        when(legBookingService.getBooking("leg-1", "user-1", false))
                .thenThrow(new IllegalArgumentException("No booking found for legId leg-1"));

        ResponseEntity<Booking> response = controller.getBooking("leg-1");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
