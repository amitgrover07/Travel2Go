package com.travel2go.backend.controller;

import com.travel2go.backend.model.WaitlistCheck;
import com.travel2go.backend.repository.WaitlistCheckRepository;
import com.travel2go.backend.repository.WaitlistWatchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the core F1 guarantee: the public endpoint is reachable with no
 * credentials (not 401), passing through the whole security chain. Runs with the
 * real SecurityConfig applied; Firestore is disabled and the repository mocked.
 */
@SpringBootTest(properties = {
        "spring.cloud.gcp.firestore.enabled=false",
        "spring.cloud.gcp.storage.enabled=false",
        "spring.cloud.gcp.core.enabled=false",
        "waitlist.ip-hash.secret=test-ip-hash-secret-at-least-32-bytes-long"
})
@MockitoBean(types = WaitlistWatchRepository.class)
class WaitlistPublicAccessTest {

    @MockitoBean
    private WaitlistCheckRepository checkRepository;

    private MockMvc mockMvc;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void checkEndpointIsPublicAndReturnsEstimate() throws Exception {
        Mockito.when(checkRepository.save(any(WaitlistCheck.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        String body = "{\"trainNo\":\"12345\",\"travelDate\":\"2026-12-25\",\"classCode\":\"SL\",\"currentPosition\":10}";

        mockMvc.perform(post("/api/v1/waitlist/check")
                        .header("X-Forwarded-For", "203.0.113.200")   // unique IP: own rate-limit bucket
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").exists())
                .andExpect(jsonPath("$.verdict").exists())
                .andExpect(jsonPath("$.shareToken").isNotEmpty());
    }

    @Test
    void badDateReturns400NotAuthError() throws Exception {
        String body = "{\"trainNo\":\"12345\",\"travelDate\":\"not-a-date\",\"classCode\":\"SL\",\"currentPosition\":10}";

        mockMvc.perform(post("/api/v1/waitlist/check")
                        .header("X-Forwarded-For", "203.0.113.201")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
