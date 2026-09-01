package com.travel2go.backend.controller;

import com.travel2go.backend.dto.CreateOrderRequest;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.InvalidWebhookSignatureException;
import com.travel2go.backend.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/order")
    public ResponseEntity<Payment> createOrder(@RequestBody CreateOrderRequest request) {
        Payment payment = paymentService.createOrder(
                request.getBookingRef(), request.getAmountPaise(), request.getMethod(), request.getQuoteToken());

        if ("REJECTED".equals(payment.getStatus())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(payment);
        }
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody byte[] rawBody, HttpServletRequest request) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        try {
            paymentService.applyWebhook(rawBody, headers);
            return ResponseEntity.ok().build();
        } catch (InvalidWebhookSignatureException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{bookingRef}")
    public ResponseEntity<Payment> getStatus(@PathVariable String bookingRef) {
        return ResponseEntity.ok(paymentService.getStatus(bookingRef));
    }

    @PostMapping("/{bookingRef}/refund")
    public ResponseEntity<Payment> refund(@PathVariable String bookingRef) {
        return ResponseEntity.ok(paymentService.refund(bookingRef));
    }
}
