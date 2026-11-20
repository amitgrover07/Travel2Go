package com.travel2go.backend.controller;

import com.travel2go.backend.dto.WaitlistCheckRequest;
import com.travel2go.backend.dto.WaitlistCheckResponse;
import com.travel2go.backend.service.IpRateLimiter;
import com.travel2go.backend.service.WaitlistService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistControllerTest {

    @Mock private WaitlistService waitlistService;
    @Mock private IpRateLimiter rateLimiter;
    @Mock private HttpServletRequest http;

    private WaitlistController controller;

    @BeforeEach
    void setUp() {
        controller = new WaitlistController(waitlistService, rateLimiter);
    }

    @Test
    void check_returnsEstimateWhenWithinRateLimit() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(http.getRemoteAddr()).thenReturn("203.0.113.5");
        WaitlistCheckRequest request = WaitlistCheckRequest.builder()
                .trainNo("12345").travelDate("2026-12-25").classCode("SL").currentPosition(10).build();
        WaitlistCheckResponse body = WaitlistCheckResponse.builder()
                .probability(0.72).verdict("likely to clear").explanation("...").shareToken("tok123").build();
        when(waitlistService.check(any(), anyString())).thenReturn(body);

        ResponseEntity<WaitlistCheckResponse> response = controller.check(request, http);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getShareToken()).isEqualTo("tok123");
    }

    @Test
    void check_returns429WhenRateLimited() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(false);
        when(http.getRemoteAddr()).thenReturn("203.0.113.5");

        ResponseEntity<WaitlistCheckResponse> response = controller.check(
                WaitlistCheckRequest.builder().build(), http);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
    }

    @Test
    void check_returns400OnInvalidInput() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(http.getRemoteAddr()).thenReturn("203.0.113.5");
        when(waitlistService.check(any(), anyString()))
                .thenThrow(new com.travel2go.backend.service.InvalidWaitlistInputException("bad trainNo"));

        ResponseEntity<WaitlistCheckResponse> response = controller.check(
                WaitlistCheckRequest.builder().trainNo("!!").build(), http);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void check_keysRateLimitOnRemoteAddrNotForwardedForHeader() {
        // A forged X-Forwarded-For must not change the rate-limit key: the real peer wins.
        when(rateLimiter.tryAcquire("198.51.100.9")).thenReturn(true);
        when(http.getRemoteAddr()).thenReturn("198.51.100.9");
        when(waitlistService.check(any(), org.mockito.ArgumentMatchers.eq("198.51.100.9")))
                .thenReturn(WaitlistCheckResponse.builder().shareToken("t").build());

        ResponseEntity<WaitlistCheckResponse> response = controller.check(
                WaitlistCheckRequest.builder().build(), http);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getShared_reproducesStoredResult() {
        WaitlistCheckResponse stored = WaitlistCheckResponse.builder()
                .probability(0.55).verdict("borderline").explanation("stored").shareToken("tok123").build();
        when(waitlistService.getByShareToken("tok123")).thenReturn(stored);

        ResponseEntity<WaitlistCheckResponse> response = controller.getShared("tok123");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProbability()).isEqualTo(0.55);
        assertThat(response.getBody().getVerdict()).isEqualTo("borderline");
    }

    @Test
    void getShared_returns404ForUnknownToken() {
        when(waitlistService.getByShareToken("missing"))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<WaitlistCheckResponse> response = controller.getShared("missing");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
