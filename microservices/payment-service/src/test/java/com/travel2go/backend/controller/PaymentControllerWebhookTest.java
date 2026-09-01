package com.travel2go.backend.controller;

import com.travel2go.backend.provider.InvalidWebhookSignatureException;
import com.travel2go.backend.security.JwtUtil;
import com.travel2go.backend.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
}
