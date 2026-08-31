package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.PaymentResult;
import com.travel2go.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final QuoteTokenService quoteTokenService;

    public Payment charge(String bookingRef, long amountPaise, String method, String quoteToken) {
        boolean quoteValid = quoteTokenService.isValid(quoteToken, bookingRef, amountPaise);

        if (!quoteValid) {
            Payment rejected = Payment.builder()
                    .bookingRef(bookingRef)
                    .method(method)
                    .status("FAILED")
                    .amountPaise(amountPaise)
                    .feePaise(0L)
                    .quoteTokenValidated(false)
                    .createdAt(new Date())
                    .build();
            return paymentRepository.save(rejected).block();
        }

        PaymentResult result = paymentProvider.charge(bookingRef, amountPaise, method);

        Payment payment = Payment.builder()
                .bookingRef(bookingRef)
                .method(method)
                .status(result.getStatus())
                .amountPaise(amountPaise)
                .feePaise(0L)
                .providerRef(result.getProviderRef())
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();

        return paymentRepository.save(payment).block();
    }
}
