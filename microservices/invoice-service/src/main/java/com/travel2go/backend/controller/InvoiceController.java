package com.travel2go.backend.controller;

import com.travel2go.backend.dto.GenerateInvoiceRequest;
import com.travel2go.backend.model.Invoice;
import com.travel2go.backend.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** Internal/admin: generate the invoice for a booking (called by the booking/payment flow). */
    @PostMapping("/invoices")
    public ResponseEntity<Invoice> generate(@RequestBody GenerateInvoiceRequest request) {
        if (!currentUserIsAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(invoiceService.generateBookingInvoice(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/invoices/{bookingRef}")
    public ResponseEntity<Invoice> get(@PathVariable String bookingRef) {
        try {
            return ResponseEntity.ok(invoiceService.getByBooking(bookingRef, currentUserId(), currentUserIsAdmin()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Internal/admin: generate the consolidated monthly GST invoice for an org. */
    @PostMapping("/org/{id}/gst-invoice/{period}")
    public ResponseEntity<Invoice> generateOrg(@PathVariable UUID id, @PathVariable String period,
                                               @RequestBody OrgInvoiceRequest request) {
        if (!currentUserIsAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(invoiceService.generateOrgGstInvoice(
                id, period, request.consolidatedChargedPaise, request.buyerGstin, request.placeOfSupply));
    }

    /**
     * Admin/internal read of an org's consolidated GST invoice. Access is gated to
     * ROLE_ADMIN in SecurityConfig (not a spoofable client header). The per-org,
     * non-admin path - deriving the caller's org from a signed JWT org claim and
     * passing it as the authorized org - is PRD-OPEN; the service still enforces that
     * guard for when it is wired.
     */
    @GetMapping("/org/{id}/gst-invoice/{period}")
    public ResponseEntity<Invoice> getOrg(@PathVariable UUID id, @PathVariable String period) {
        // Admin/internal only (SecurityConfig also gates this). An admin may read any
        // org's invoice; the per-org, non-admin path (caller org from a signed JWT
        // claim) is PRD-OPEN. The id-as-authorized-org call below is intentional under
        // this admin-only gate and is not a user-controlled authorization decision.
        if (!currentUserIsAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(invoiceService.getOrgGstInvoice(id, period, id));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public static class OrgInvoiceRequest {
        public long consolidatedChargedPaise;
        public String buyerGstin;
        public String placeOfSupply;
    }
}
