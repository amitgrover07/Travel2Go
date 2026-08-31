package com.travel2go.backend.provider;

import lombok.Value;

/**
 * Legacy DTO used by PaymentService.
 * TODO: Update PaymentService to work with the new PaymentProvider interface.
 */
@Value
public class PaymentResult {
    String status;
    String providerRef;
}
