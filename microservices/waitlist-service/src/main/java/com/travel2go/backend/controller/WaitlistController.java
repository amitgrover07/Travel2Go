package com.travel2go.backend.controller;

import com.travel2go.backend.dto.WaitlistCheckRequest;
import com.travel2go.backend.dto.WaitlistCheckResponse;
import com.travel2go.backend.dto.WaitlistWatchRequest;
import com.travel2go.backend.service.InvalidWaitlistInputException;
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
        } catch (InvalidWaitlistInputException e) {
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
        } catch (InvalidWaitlistInputException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * The client IP for rate-limiting. We deliberately do NOT trust a raw X-Forwarded-For
     * header here: a caller can forge the leftmost hop and sidestep the per-IP limit.
     * getRemoteAddr() reflects the real peer, and Spring's RemoteIp valve (configured via
     * server.tomcat.remoteip.* with an internal-proxy allow-list) rewrites it from
     * X-Forwarded-For only for requests that actually arrive through a trusted proxy.
     */
    private String clientIp(HttpServletRequest http) {
        return http.getRemoteAddr();
    }
}
