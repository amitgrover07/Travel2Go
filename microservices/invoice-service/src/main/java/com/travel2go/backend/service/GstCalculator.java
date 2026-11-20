package com.travel2go.backend.service;

import org.springframework.stereotype.Component;

/**
 * GST tax split (F7). Treats the charged amount as tax-inclusive and derives the
 * taxable value + tax so that taxable + tax == charged exactly (reconciliation is
 * guaranteed by construction). Intra-state supply splits the tax into equal CGST +
 * SGST; inter-state supply is a single IGST. Place of supply is the destination
 * state; intra-state is sellerState == placeOfSupply.
 */
@Component
public class GstCalculator {

    public GstBreakdown compute(long chargedPaise, int rateBps, String sellerStateCode, String placeOfSupplyStateCode) {
        if (chargedPaise < 0) {
            throw new IllegalArgumentException("chargedPaise must be non-negative");
        }
        long denom = 10_000L + rateBps;
        // Round to nearest paise; totalTax is the remainder so taxable + tax == charged.
        long taxable = (chargedPaise * 10_000L + denom / 2) / denom;
        long totalTax = chargedPaise - taxable;

        boolean intraState = sellerStateCode != null && sellerStateCode.equals(placeOfSupplyStateCode);
        long cgst = 0;
        long sgst = 0;
        long igst = 0;
        if (intraState) {
            cgst = totalTax / 2;
            sgst = totalTax - cgst; // odd paise lands on SGST so the halves still sum to totalTax
        } else {
            igst = totalTax;
        }
        return new GstBreakdown(taxable, cgst, sgst, igst, taxable + cgst + sgst + igst);
    }

    public record GstBreakdown(long taxableValuePaise, long cgstPaise, long sgstPaise, long igstPaise, long totalPaise) {
        public boolean intraState() {
            return igstPaise == 0 && (cgstPaise > 0 || sgstPaise > 0);
        }
    }
}
