package com.travel2go.backend.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SandboxUpiProvider implements PaymentProvider {

    // Stubbed pending a real UPI provider decision (Razorpay vs Cashfree) - see OPEN_QUESTIONS.md.
    // Always succeeds so trip-booking and refund flows can be built and tested end-to-end now.
    @Override
    public PaymentResult charge(String reference, long amountPaise, String method) {
        return new PaymentResult("SUCCESS", "SANDBOX-" + UUID.randomUUID());
    }
}
