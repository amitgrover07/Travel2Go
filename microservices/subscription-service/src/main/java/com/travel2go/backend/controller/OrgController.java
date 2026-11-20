package com.travel2go.backend.controller;

import com.travel2go.backend.model.CreditAccount;
import com.travel2go.backend.model.Org;
import com.travel2go.backend.model.OrgMember;
import com.travel2go.backend.service.OrgSpendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Business org management + the spend/approval/credit gate the booking flow consults (F6).
 */
@RestController
@RequestMapping("/api/orgs")
@RequiredArgsConstructor
public class OrgController {

    private final OrgSpendService orgSpendService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping
    public ResponseEntity<Org> createOrg(@RequestBody CreateOrgRequest req) {
        return ResponseEntity.ok(orgSpendService.createOrg(req.name, currentUserId(), req.namedContact));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<OrgMember> addMember(@PathVariable UUID id, @RequestBody MemberRequest req) {
        try {
            return ResponseEntity.ok(orgSpendService.addMember(id, currentUserId(), req.userId, req.role));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PutMapping("/{id}/policy")
    public ResponseEntity<Void> setPolicy(@PathVariable UUID id, @RequestBody PolicyRequest req) {
        try {
            orgSpendService.setPolicy(id, currentUserId(), req.perTripCapPaise, req.monthlyCapPaise);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PutMapping("/{id}/credit")
    public ResponseEntity<Void> setCreditLimit(@PathVariable UUID id, @RequestBody CreditLimitRequest req) {
        try {
            orgSpendService.setCreditLimit(id, currentUserId(), req.limitPaise);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * The booking flow calls this before confirming an org booking.
     *
     * PRD-OPEN: the authoritative amount must be resolved server-side from the
     * booking/payment record (by bookingRef) rather than trusted from the caller,
     * so a member cannot understate an amount to slip past the spend caps / credit
     * line. Until that cross-service lookup is wired, this at least rejects a
     * non-positive amount; the endpoint is intended for the internal booking flow.
     */
    @PostMapping("/{id}/bookings/evaluate")
    public ResponseEntity<Map<String, String>> evaluate(@PathVariable UUID id, @RequestBody EvaluateRequest req) {
        if (req.bookingRef == null || req.bookingRef.isBlank() || req.amountPaise <= 0) {
            return ResponseEntity.badRequest().build();
        }
        try {
            OrgSpendService.Decision decision =
                    orgSpendService.evaluateBooking(id, currentUserId(), req.bookingRef, req.amountPaise);
            return ResponseEntity.ok(Map.of("bookingRef", req.bookingRef, "decision", decision.name()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/{id}/approvals/{bookingRef}")
    public ResponseEntity<Map<String, String>> approve(@PathVariable UUID id, @PathVariable String bookingRef) {
        try {
            OrgSpendService.Decision decision = orgSpendService.approve(id, bookingRef, currentUserId());
            return ResponseEntity.ok(Map.of("bookingRef", bookingRef, "decision", decision.name()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/invoice/{period}")
    public ResponseEntity<Map<String, Object>> invoice(@PathVariable UUID id, @PathVariable String period) {
        try {
            orgSpendService.assertMember(id, currentUserId()); // billing data is members-only
            long total = orgSpendService.consolidatedBillPaise(id, period);
            return ResponseEntity.ok(Map.of(
                    "orgId", id.toString(), "period", period, "totalPaise", total,
                    "lineCount", orgSpendService.bookingsForPeriod(id, period).size()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/{id}/credit")
    public ResponseEntity<CreditAccount> credit(@PathVariable UUID id) {
        try {
            orgSpendService.assertMember(id, currentUserId()); // credit data is members-only
            return ResponseEntity.ok(orgSpendService.credit(id));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public static class CreateOrgRequest { public String name; public String namedContact; }
    public static class MemberRequest { public String userId; public String role; }
    public static class PolicyRequest { public long perTripCapPaise; public long monthlyCapPaise; }
    public static class CreditLimitRequest { public long limitPaise; }
    public static class EvaluateRequest { public String bookingRef; public long amountPaise; }
}
