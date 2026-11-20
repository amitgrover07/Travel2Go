package com.travel2go.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String fy;

    @Column(nullable = false)
    private String type; // RECEIPT | GST_INVOICE | ORG_GST_INVOICE

    @Column(name = "booking_ref")
    private String bookingRef;

    @Column(name = "owner_user_id")
    private String ownerUserId; // the booking owner; null for org-level invoices

    @Column(name = "org_id")
    private UUID orgId;

    private String period;

    @Column(name = "seller_gstin")
    private String sellerGstin;

    @Column(name = "buyer_gstin")
    private String buyerGstin;

    @Column(name = "hsn_sac")
    private String hsnSac;

    @Column(name = "place_of_supply")
    private String placeOfSupply;

    @Column(name = "taxable_value_paise", nullable = false)
    private Long taxableValuePaise;

    @Column(name = "cgst_paise", nullable = false)
    @Builder.Default
    private Long cgstPaise = 0L;

    @Column(name = "sgst_paise", nullable = false)
    @Builder.Default
    private Long sgstPaise = 0L;

    @Column(name = "igst_paise", nullable = false)
    @Builder.Default
    private Long igstPaise = 0L;

    @Column(name = "total_paise", nullable = false)
    private Long totalPaise;

    @Column(name = "pdf_ref")
    private String pdfRef;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
