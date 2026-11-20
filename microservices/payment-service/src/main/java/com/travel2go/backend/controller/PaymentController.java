package com.travel2go.backend.controller;

import com.travel2go.backend.dto.CreateOrderRequest;
import com.travel2go.backend.dto.FloatBalanceResponse;
import com.travel2go.backend.dto.FloatTopUpRequest;
import com.travel2go.backend.dto.RefundStatusResponse;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.model.Refund;
import com.travel2go.backend.provider.InvalidWebhookSignatureException;
import com.travel2go.backend.service.PaymentConflictException;
import com.travel2go.backend.service.PaymentService;
import com.travel2go.backend.service.RefundFloatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundFloatService refundFloatService;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean currentUserIsAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping("/order")
    public ResponseEntity<Payment> createOrder(@RequestBody CreateOrderRequest request) {
        if (request.getAmountPaise() == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Payment payment = paymentService.createOrder(
                    request.getBookingRef(), request.getAmountPaise(), request.getMethod(), request.getQuoteToken(),
                    currentUserId());

            if ("REJECTED".equals(payment.getStatus())) {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(payment);
            }
            return ResponseEntity.ok(payment);
        } catch (PaymentConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
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
        } catch (InvalidWebhookSignatureException | UncheckedIOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{bookingRef}")
    public ResponseEntity<Payment> getStatus(@PathVariable String bookingRef) {
        try {
            return ResponseEntity.ok(paymentService.getStatus(bookingRef, currentUserId(), currentUserIsAdmin()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{bookingRef}/refund")
    public ResponseEntity<Payment> refund(@PathVariable String bookingRef,
                                          @RequestParam(required = false) String trigger) {
        return ResponseEntity.ok(paymentService.refund(bookingRef, trigger));
    }

    @GetMapping("/{bookingRef}/refund/status")
    public ResponseEntity<RefundStatusResponse> refundStatus(@PathVariable String bookingRef) {
        try {
            Optional<Refund> refund = paymentService.getRefund(bookingRef, currentUserId(), currentUserIsAdmin());
            if (refund.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Refund r = refund.get();
            return ResponseEntity.ok(RefundStatusResponse.builder()
                    .slaStatus(r.getSlaStatus())
                    .slaDueAt(r.getSlaDueAt())
                    .breached("BREACHED".equals(r.getSlaStatus()))
                    .trigger(r.getTrigger())
                    .amountPaise(r.getAmountPaise())
                    .providerStatus(r.getStatus())
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Admin/ops: mark a refund settled, stopping its SLA countdown. */
    @PostMapping("/{bookingRef}/refund/settle")
    public ResponseEntity<Void> settleRefund(@PathVariable String bookingRef) {
        if (!currentUserIsAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        paymentService.markRefundSettled(bookingRef);
        return ResponseEntity.ok().build();
    }

    /** Admin/internal: ring-fenced refund float balance + low-float flag. */
    @GetMapping("/refund-float/balance")
    public ResponseEntity<FloatBalanceResponse> floatBalance() {
        if (!currentUserIsAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(FloatBalanceResponse.builder()
                .balancePaise(refundFloatService.balancePaise())
                .low(refundFloatService.isLow())
                .lowThresholdPaise(refundFloatService.lowThresholdPaise())
                .build());
    }

    /** Admin/internal: credit the ring-fenced refund float. */
    @PostMapping("/refund-float/topup")
    public ResponseEntity<FloatBalanceResponse> floatTopUp(@RequestBody FloatTopUpRequest request) {
        if (!currentUserIsAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.getAmountPaise() == null || request.getAmountPaise() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        refundFloatService.credit(request.getAmountPaise());
        return ResponseEntity.ok(FloatBalanceResponse.builder()
                .balancePaise(refundFloatService.balancePaise())
                .low(refundFloatService.isLow())
                .lowThresholdPaise(refundFloatService.lowThresholdPaise())
                .build());
    }
}
