package com.travel2go.backend.controller;

import com.travel2go.backend.dto.EntitlementsResponse;
import com.travel2go.backend.dto.SubscribeRequest;
import com.travel2go.backend.model.Subscription;
import com.travel2go.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping
    public ResponseEntity<Subscription> subscribe(@RequestBody(required = false) SubscribeRequest request) {
        String planCode = (request != null && request.getPlanCode() != null) ? request.getPlanCode() : "FAMILY_PLUS";
        String coupon = request != null ? request.getCouponCode() : null;
        try {
            return ResponseEntity.ok(subscriptionService.subscribe(currentUserId(), planCode, coupon));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Subscription> me() {
        Optional<Subscription> sub = subscriptionService.getMine(currentUserId());
        return sub.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/me/entitlements")
    public ResponseEntity<EntitlementsResponse> entitlements() {
        String userId = currentUserId();
        List<String> ents = subscriptionService.entitlements(userId);
        return ResponseEntity.ok(EntitlementsResponse.builder()
                .userId(userId)
                .subscribed(!ents.isEmpty())
                .entitlements(ents)
                .build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Subscription> cancel(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(subscriptionService.cancel(id, currentUserId()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Renewal billing webhook (admin/internal). The billing period makes it idempotent. */
    @PostMapping("/{id}/renew")
    public ResponseEntity<Subscription> renew(@PathVariable UUID id,
                                              @RequestParam(required = false) Integer period) {
        if (!currentUserIsAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(subscriptionService.renew(id, period));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
