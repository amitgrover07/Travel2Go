package com.travel2go.backend.controller;

import com.travel2go.backend.dto.WaitlistCheckRequest;
import com.travel2go.backend.dto.WaitlistCheckResponse;
import com.travel2go.backend.dto.WaitlistWatchRequest;
import com.travel2go.backend.service.IpRateLimiter;
import com.travel2go.backend.service.WaitlistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public, login-free Waitlist Checker API (F1). Versioned under /api/v1 and
 * rate-limited per client IP. No authentication is required or applied here.
 */
@RestController
@RequestMapping("/api/v1/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final IpRateLimiter rateLimiter;

    @PostMapping("/check")
    public ResponseEntity<WaitlistCheckResponse> check(@RequestBody WaitlistCheckRequest request,
                                                       HttpServletRequest http) {
        String clientIp = clientIp(http);
        if (!rateLimiter.tryAcquire(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        try {
            return ResponseEntity.ok(waitlistService.check(request, clientIp));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{shareToken}")
    public ResponseEntity<WaitlistCheckResponse> getShared(@PathVariable String shareToken) {
        try {
            return ResponseEntity.ok(waitlistService.getByShareToken(shareToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{shareToken}/watch")
    public ResponseEntity<Void> watch(@PathVariable String shareToken,
                                      @RequestBody(required = false) WaitlistWatchRequest request,
                                      HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(clientIp(http))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String contact = request != null ? request.getContact() : null;
        String channel = request != null ? request.getChannel() : null;
        try {
            waitlistService.watch(shareToken, contact, channel);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** First hop of X-Forwarded-For (gateway/CDN) if present, else the socket address. */
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
