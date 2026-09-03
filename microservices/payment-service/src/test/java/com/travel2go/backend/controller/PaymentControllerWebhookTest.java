package com.travel2go.backend.controller;

import com.travel2go.backend.provider.InvalidWebhookSignatureException;
import com.travel2go.backend.security.JwtUtil;
import com.travel2go.backend.service.PaymentConflictException;
import com.travel2go.backend.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.UncheckedIOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@org.springframework.context.annotation.Import(com.travel2go.backend.security.SecurityConfig.class)
class PaymentControllerWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void webhook_passesExactRawBodyBytesToService() throws Exception {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes();

        mockMvc.perform(post("/api/payments/webhook")
                        .content(body)
                        .header("X-Razorpay-Signature", "sig-123"))
                .andExpect(status().isOk());

        verify(paymentService).applyWebhook(eq(body), any());
    }

    @Test
    void webhook_invalidSignatureReturns400() throws Exception {
        doThrow(new InvalidWebhookSignatureException("bad sig"))
                .when(paymentService).applyWebhook(any(), any());

        mockMvc.perform(post("/api/payments/webhook")
                        .content("{}".getBytes())
                        .header("X-Razorpay-Signature", "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_malformedBodyReturns400() throws Exception {
        doThrow(new UncheckedIOException("Malformed webhook payload", new java.io.IOException("bad json")))
                .when(paymentService).applyWebhook(any(), any());

        mockMvc.perform(post("/api/payments/webhook")
                        .content("not-json".getBytes())
                        .header("X-Razorpay-Signature", "sig-123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_missingAmountPaiseReturns400() throws Exception {
        when(jwtUtil.extractUsername(any())).thenReturn("test-user");
        when(jwtUtil.extractRoles(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/payments/order")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingRef\":\"leg-1\",\"method\":\"UPI\",\"quoteToken\":\"tok\"}"))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).createOrder(any(), anyLong(), any(), any(), any());
    }

    @Test
    void createOrder_conflictingOwnerReturns409() throws Exception {
        when(jwtUtil.extractUsername(any())).thenReturn("test-user");
        when(jwtUtil.extractRoles(any())).thenReturn(List.of());
        when(paymentService.createOrder(any(), anyLong(), any(), any(), any()))
                .thenThrow(new PaymentConflictException("Payment for bookingRef leg-1 is already claimed by another user"));

        mockMvc.perform(post("/api/payments/order")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingRef\":\"leg-1\",\"amountPaise\":150000,\"method\":\"UPI\",\"quoteToken\":\"tok\"}"))
                .andExpect(status().isConflict());
    }
}
