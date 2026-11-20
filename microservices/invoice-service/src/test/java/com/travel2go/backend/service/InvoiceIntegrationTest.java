package com.travel2go.backend.service;

import com.travel2go.backend.dto.GenerateInvoiceRequest;
import com.travel2go.backend.model.Invoice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F7 against a real Postgres: gapless per-FY numbering, idempotent generation,
 * receipt vs GST invoice, consolidated org invoice reconciliation, and the
 * cross-org authorization guard.
 */
@Testcontainers
@SpringBootTest
class InvoiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "test-jwt-secret-0123456789abcdef0123456789abcdef");
    }

    @Autowired private InvoiceService invoiceService;

    private GenerateInvoiceRequest booking(String ref, long charged, String buyerGstin, String pos) {
        return GenerateInvoiceRequest.builder()
                .bookingRef(ref).ownerUserId("u-owner").chargedPaise(charged)
                .buyerGstin(buyerGstin).placeOfSupply(pos).build();
    }

    @Test
    void consumerBooking_getsASimpleReceipt_businessBooking_getsAGstInvoice() {
        Invoice receipt = invoiceService.generateBookingInvoice(booking("bk-c1", 105_000L, null, "07"));
        assertThat(receipt.getType()).isEqualTo("RECEIPT");
        assertThat(receipt.getTaxableValuePaise()).isEqualTo(105_000L);
        assertThat(receipt.getCgstPaise() + receipt.getSgstPaise() + receipt.getIgstPaise()).isZero();

        Invoice gst = invoiceService.generateBookingInvoice(booking("bk-b1", 105_000L, "27ZZZZZ1111Z1Z0", "27"));
        assertThat(gst.getType()).isEqualTo("GST_INVOICE");
        assertThat(gst.getIgstPaise()).isEqualTo(5_000L); // inter-state (seller 07, supply 27)
        assertThat(gst.getTaxableValuePaise() + gst.getIgstPaise()).isEqualTo(105_000L); // reconciles
    }

    @Test
    void invoiceNumbersAreUniqueAndGaplessWithinAFy() {
        Invoice a = invoiceService.generateBookingInvoice(booking("seq-a", 100_000L, null, "07"));
        Invoice b = invoiceService.generateBookingInvoice(booking("seq-b", 100_000L, null, "07"));
        Invoice c = invoiceService.generateBookingInvoice(booking("seq-c", 100_000L, null, "07"));

        long na = tail(a.getNumber());
        long nb = tail(b.getNumber());
        long nc = tail(c.getNumber());
        assertThat(nb).isEqualTo(na + 1);
        assertThat(nc).isEqualTo(nb + 1); // gapless
        assertThat(a.getNumber()).isNotEqualTo(b.getNumber()); // unique
    }

    @Test
    void generationIsIdempotentPerBooking_andBurnsNoExtraNumber() {
        Invoice first = invoiceService.generateBookingInvoice(booking("idem-1", 100_000L, null, "07"));
        Invoice again = invoiceService.generateBookingInvoice(booking("idem-1", 100_000L, null, "07"));
        assertThat(again.getNumber()).isEqualTo(first.getNumber());
        assertThat(again.getId()).isEqualTo(first.getId());
    }

    @Test
    void orgGstInvoice_aggregatesAndReconciles_andIsCrossOrgProtected() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        Invoice inv = invoiceService.generateOrgGstInvoice(orgA, "2026-11", 210_000L, "27ZZZZZ1111Z1Z0", "07");
        assertThat(inv.getType()).isEqualTo("ORG_GST_INVOICE");
        assertThat(inv.getCgstPaise() + inv.getSgstPaise()).isEqualTo(210_000L - inv.getTaxableValuePaise()); // intra
        assertThat(inv.getTaxableValuePaise() + inv.getCgstPaise() + inv.getSgstPaise() + inv.getIgstPaise())
                .isEqualTo(210_000L);

        // Another org can never read org A's invoice.
        assertThatThrownBy(() -> invoiceService.getOrgGstInvoice(orgA, "2026-11", orgB))
                .isInstanceOf(AccessDeniedException.class);
        // The owning org can.
        assertThat(invoiceService.getOrgGstInvoice(orgA, "2026-11", orgA).getOrgId()).isEqualTo(orgA);
    }

    @Test
    void bookingInvoiceRead_isOwnershipChecked() {
        invoiceService.generateBookingInvoice(booking("own-1", 100_000L, null, "07")); // owner u-owner

        assertThat(invoiceService.getByBooking("own-1", "u-owner", false).getBookingRef()).isEqualTo("own-1");
        // A different user cannot read it (not revealed).
        assertThatThrownBy(() -> invoiceService.getByBooking("own-1", "someone-else", false))
                .isInstanceOf(IllegalArgumentException.class);
        // An admin/service caller can.
        assertThat(invoiceService.getByBooking("own-1", "admin", true).getBookingRef()).isEqualTo("own-1");
    }

    private long tail(String number) {
        return Long.parseLong(number.substring(number.lastIndexOf('/') + 1));
    }
}
