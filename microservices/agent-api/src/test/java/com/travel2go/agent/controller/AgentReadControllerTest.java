package com.travel2go.agent.controller;

import com.travel2go.agent.client.BookingStatusClient;
import com.travel2go.agent.client.UpstreamUnavailableException;
import com.travel2go.agent.client.WaitlistClient;
import com.travel2go.agent.dto.AgentWaitlistRequest;
import com.travel2go.agent.dto.AgentWaitlistResponse;
import com.travel2go.backend.model.Booking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReadControllerTest {

    @Mock private WaitlistClient waitlistClient;
    @Mock private BookingStatusClient bookingStatusClient;

    @Test
    void waitlist_returnsEstimate() {
        AgentWaitlistController controller = new AgentWaitlistController(waitlistClient);
        when(waitlistClient.check(any())).thenReturn(
                AgentWaitlistResponse.builder().probability(0.6).verdict("borderline").shareToken("s").build());

        ResponseEntity<AgentWaitlistResponse> response =
                controller.check(AgentWaitlistRequest.builder().trainNo("12345").build());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getVerdict()).isEqualTo("borderline");
    }

    @Test
    void waitlist_returns503WhenUpstreamDown() {
        AgentWaitlistController controller = new AgentWaitlistController(waitlistClient);
        when(waitlistClient.check(any()))
                .thenThrow(new UpstreamUnavailableException("down", new RuntimeException()));

        ResponseEntity<AgentWaitlistResponse> response =
                controller.check(AgentWaitlistRequest.builder().build());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void journey_reportsStatusWhenBookingFound() {
        AgentJourneyController controller = new AgentJourneyController(bookingStatusClient);
        when(bookingStatusClient.getByLegId("leg-1")).thenReturn(
                Booking.builder().legId("leg-1").status("CONFIRMED").amountPaise(150000L).build());

        var response = controller.status("leg-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAvailable()).isTrue();
        assertThat(response.getBody().getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void journey_reportsUnavailableWhenUpstreamDegraded() {
        AgentJourneyController controller = new AgentJourneyController(bookingStatusClient);
        when(bookingStatusClient.getByLegId("leg-1")).thenReturn(null); // circuit-breaker fallback

        var response = controller.status("leg-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAvailable()).isFalse();
    }
}
