package com.travel2go.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateInvoiceRequest {
    private String bookingRef;
    private Long chargedPaise;
    private String buyerGstin;     // present -> full GST invoice; absent -> simple receipt
    private String placeOfSupply;  // destination state code (e.g. 07 for Delhi)
}
