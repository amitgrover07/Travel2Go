package com.travel2go.backend.service;

import com.travel2go.backend.dto.GenerateInvoiceRequest;
import com.travel2go.backend.model.Invoice;
import com.travel2go.backend.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

/**
 * Generates consumer receipts and GST-compliant invoices (F7). Invoice numbers are
 * gapless per FY and generation is idempotent per booking / org-period, so a second
 * request returns the existing invoice and never burns another number. The tax split
 * always reconciles to the charged amount (see {@link GstCalculator}).
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberService numberService;
    private final GstCalculator gstCalculator;

    @Value("${invoice.seller.gstin:07AAAAA0000A1Z5}")
    private String sellerGstin;

    @Value("${invoice.seller.state-code:07}")
    private String sellerStateCode;

    @Value("${invoice.gst.rate-bps:500}")
    private int gstRateBps;

    @Value("${invoice.hsn-sac:9964}")
    private String hsnSac;

    @Transactional
    public Invoice generateBookingInvoice(GenerateInvoiceRequest request) {
        if (request.getBookingRef() == null || request.getChargedPaise() == null) {
            throw new IllegalArgumentException("bookingRef and chargedPaise are required");
        }
        return invoiceRepository.findByBookingRef(request.getBookingRef())
                .orElseGet(() -> createBookingInvoice(request));
    }

    private Invoice createBookingInvoice(GenerateInvoiceRequest request) {
        boolean gst = request.getBuyerGstin() != null && !request.getBuyerGstin().isBlank();
        String placeOfSupply = request.getPlaceOfSupply() != null ? request.getPlaceOfSupply() : sellerStateCode;
        LocalDate today = LocalDate.now();
        String fy = InvoiceNumberService.financialYear(today);
        String number = numberService.nextNumber(fy);

        Invoice.InvoiceBuilder builder = Invoice.builder()
                .number(number).fy(fy)
                .bookingRef(request.getBookingRef())
                .ownerUserId(request.getOwnerUserId())
                .placeOfSupply(placeOfSupply)
                .pdfRef("invoices/" + number.replace("/", "_") + ".pdf")
                .createdAt(new Date());

        if (gst) {
            GstCalculator.GstBreakdown b = gstCalculator.compute(
                    request.getChargedPaise(), gstRateBps, sellerStateCode, placeOfSupply);
            builder.type("GST_INVOICE")
                    .sellerGstin(sellerGstin).buyerGstin(request.getBuyerGstin()).hsnSac(hsnSac)
                    .taxableValuePaise(b.taxableValuePaise())
                    .cgstPaise(b.cgstPaise()).sgstPaise(b.sgstPaise()).igstPaise(b.igstPaise())
                    .totalPaise(b.totalPaise());
        } else {
            builder.type("RECEIPT")
                    .taxableValuePaise(request.getChargedPaise())
                    .cgstPaise(0L).sgstPaise(0L).igstPaise(0L)
                    .totalPaise(request.getChargedPaise());
        }
        return invoiceRepository.save(builder.build());
    }

    /**
     * Ownership-checked read: only the booking's owner (or an admin/service caller)
     * may fetch its invoice - a user can never read another user's invoice.
     */
    public Invoice getByBooking(String bookingRef, String requestingUserId, boolean isAdmin) {
        Invoice invoice = invoiceRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new IllegalArgumentException("No invoice for booking " + bookingRef));
        if (!isAdmin && (invoice.getOwnerUserId() == null || !invoice.getOwnerUserId().equals(requestingUserId))) {
            // Do not reveal existence to a non-owner.
            throw new IllegalArgumentException("No invoice for booking " + bookingRef);
        }
        return invoice;
    }

    /** Consolidated monthly GST invoice for an org (ties to F6's consolidated bill). Idempotent per org+period. */
    @Transactional
    public Invoice generateOrgGstInvoice(UUID orgId, String period, long consolidatedChargedPaise,
                                         String buyerGstin, String placeOfSupply) {
        return invoiceRepository.findByOrgIdAndPeriod(orgId, period).orElseGet(() -> {
            String pos = placeOfSupply != null ? placeOfSupply : sellerStateCode;
            LocalDate today = LocalDate.now();
            String fy = InvoiceNumberService.financialYear(today);
            String number = numberService.nextNumber(fy);
            GstCalculator.GstBreakdown b = gstCalculator.compute(
                    consolidatedChargedPaise, gstRateBps, sellerStateCode, pos);
            return invoiceRepository.save(Invoice.builder()
                    .number(number).fy(fy).type("ORG_GST_INVOICE")
                    .orgId(orgId).period(period)
                    .sellerGstin(sellerGstin).buyerGstin(buyerGstin).hsnSac(hsnSac)
                    .placeOfSupply(pos)
                    .taxableValuePaise(b.taxableValuePaise())
                    .cgstPaise(b.cgstPaise()).sgstPaise(b.sgstPaise()).igstPaise(b.igstPaise())
                    .totalPaise(b.totalPaise())
                    .pdfRef("invoices/" + number.replace("/", "_") + ".pdf")
                    .createdAt(new Date()).build());
        });
    }

    /**
     * Fetch an org GST invoice, authorizing that the caller is acting for that org -
     * one org's invoice is never exposed to another (cross-org IDOR guard).
     */
    public Invoice getOrgGstInvoice(UUID orgId, String period, UUID callerAuthorizedOrgId) {
        if (callerAuthorizedOrgId == null || !callerAuthorizedOrgId.equals(orgId)) {
            throw new AccessDeniedException("Not authorized for org " + orgId);
        }
        return invoiceRepository.findByOrgIdAndPeriod(orgId, period)
                .orElseThrow(() -> new IllegalArgumentException("No org invoice for " + orgId + "/" + period));
    }
}
