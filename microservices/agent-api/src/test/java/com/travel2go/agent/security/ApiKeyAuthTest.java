package com.travel2go.agent.security;

import com.travel2go.agent.client.BookingStatusClient;
import com.travel2go.agent.client.TripQuoteClient;
import com.travel2go.agent.client.WaitlistClient;
import com.travel2go.backend.dto.LegQuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F2 auth/scope ACs: agent-key auth works and is scoped; a key without the book
 * scope cannot reach any mutation; the published OpenAPI doc is public.
 */
@SpringBootTest
class ApiKeyAuthTest {

    @MockitoBean private WaitlistClient waitlistClient;
    @MockitoBean private BookingStatusClient bookingStatusClient;
    @MockitoBean private TripQuoteClient tripQuoteClient;

    private MockMvc mockMvc;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach
    void stubQuote() {
        when(tripQuoteClient.quoteLeg(anyString())).thenReturn(LegQuoteResponse.builder()
                .legId("leg-1").pricePaise(150000L).currency("INR")
                .quoteToken("trip-signed-token").feePaise(0L).build());
    }

    private static final String QUOTE_BODY = "{\"legId\":\"leg-1\"}";

    @Test
    void quote_withoutKey_is401() throws Exception {
        mockMvc.perform(post("/api/agent/v1/quote")
                        .contentType(MediaType.APPLICATION_JSON).content(QUOTE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quote_withInvalidKey_is401() throws Exception {
        mockMvc.perform(post("/api/agent/v1/quote")
                        .header("X-Agent-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content(QUOTE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quote_withReadKey_is200_andIssuesToken() throws Exception {
        mockMvc.perform(post("/api/agent/v1/quote")
                        .header("X-Agent-Key", "read-key-value")
                        .contentType(MediaType.APPLICATION_JSON).content(QUOTE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteToken").isNotEmpty())
                .andExpect(jsonPath("$.feePaise").value(0));
    }

    @Test
    void mutation_withReadOnlyKey_is403() throws Exception {
        mockMvc.perform(post("/api/agent/v1/bookings")
                        .header("X-Agent-Key", "read-key-value")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutation_withBookKey_passesScopeGate() throws Exception {
        // 501 (not 403/401) proves the book-scoped key cleared auth + scope and reached the handler.
        mockMvc.perform(post("/api/agent/v1/bookings")
                        .header("X-Agent-Key", "book-key-value")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void openApiDoc_isPublic() throws Exception {
        mockMvc.perform(get("/api/agent/v1/openapi.yaml"))
                .andExpect(status().isOk());
    }
}
