package com.travel2go.backend.controller;

import com.travel2go.backend.dto.GenerateInvoiceRequest;
import com.travel2go.backend.model.Invoice;
import com.travel2go.backend.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /** Internal/admin: generate the invoice for a booking (called by the booking/payment flow). */
    @PostMapping("/invoices")
    public ResponseEntity<Invoice> generate(@RequestBody GenerateInvoiceRequest request) {
        try {
            return ResponseEntity.ok(invoiceService.generateBookingInvoice(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/invoices/{bookingRef}")
    public ResponseEntity<Invoice> get(@PathVariable String bookingRef) {
        try {
            return ResponseEntity.ok(invoiceService.getByBooking(bookingRef));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Internal/admin: generate the consolidated monthly GST invoice for an org. */
    @PostMapping("/org/{id}/gst-invoice/{period}")
    public ResponseEntity<Invoice> generateOrg(@PathVariable UUID id, @PathVariable String period,
                                               @RequestBody OrgInvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.generateOrgGstInvoice(
                id, period, request.consolidatedChargedPaise, request.buyerGstin, request.placeOfSupply));
    }

    @GetMapping("/org/{id}/gst-invoice/{period}")
    public ResponseEntity<Invoice> getOrg(@PathVariable UUID id, @PathVariable String period,
                                          @RequestHeader(value = "X-Org-Id", required = false) UUID callerOrgId) {
        try {
            return ResponseEntity.ok(invoiceService.getOrgGstInvoice(id, period, callerOrgId));
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
