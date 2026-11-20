package com.travel2go.agent.service;

import com.travel2go.agent.client.TripQuoteClient;
import com.travel2go.agent.client.UpstreamUnavailableException;
import com.travel2go.agent.dto.AgentQuoteRequest;
import com.travel2go.agent.dto.AgentQuoteResponse;
import com.travel2go.backend.dto.LegQuoteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The agent quote must never trust a caller price: the service delegates to
 * trip-service (the price authority) and returns exactly what it signed. There is
 * no local signing and no secret in agent-api (G2 trust boundary).
 */
@ExtendWith(MockitoExtension.class)
class AgentQuoteServiceTest {

    @Mock private TripQuoteClient tripQuoteClient;

    @Test
    void returnsTheServerSignedQuoteFromTripService() {
        AgentQuoteService service = new AgentQuoteService(tripQuoteClient);
        when(tripQuoteClient.quoteLeg("leg-9")).thenReturn(LegQuoteResponse.builder()
                .legId("leg-9").pricePaise(275000L).currency("INR")
                .quoteToken("trip-signed-token").feePaise(0L).build());

        AgentQuoteResponse quote = service.quote(AgentQuoteRequest.builder().legId("leg-9").build());

        assertThat(quote.getQuoteToken()).isEqualTo("trip-signed-token"); // trip-service's token, verbatim
        assertThat(quote.getPricePaise()).isEqualTo(275000L);              // server price, not caller-chosen
        assertThat(quote.getFeePaise()).isZero();                          // G1
        assertThat(quote.getCurrency()).isEqualTo("INR");
    }

    @Test
    void rejectsMissingLegId() {
        AgentQuoteService service = new AgentQuoteService(tripQuoteClient);
        assertThatThrownBy(() -> service.quote(AgentQuoteRequest.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propagatesUpstreamUnavailable() {
        AgentQuoteService service = new AgentQuoteService(tripQuoteClient);
        when(tripQuoteClient.quoteLeg("leg-1"))
                .thenThrow(new UpstreamUnavailableException("down", new RuntimeException()));
        assertThatThrownBy(() -> service.quote(AgentQuoteRequest.builder().legId("leg-1").build()))
                .isInstanceOf(UpstreamUnavailableException.class);
    }
}
