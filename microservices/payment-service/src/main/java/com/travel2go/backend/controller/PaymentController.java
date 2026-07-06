package com.travel2go.backend.controller;

import com.travel2go.backend.dto.ChargeRequest;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Payment> charge(@RequestBody ChargeRequest request) {
        Payment payment = paymentService.charge(
                request.getBookingRef(), request.getAmountPaise(), request.getMethod(), request.getQuoteToken());

        if ("FAILED".equals(payment.getStatus())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(payment);
        }
        return ResponseEntity.ok(payment);
    }
}
