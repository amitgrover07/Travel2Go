package com.travel2go.backend.controller;

import com.travel2go.backend.security.JwtUtil;
import com.travel2go.backend.service.PaymentService;
import com.travel2go.backend.service.ReconciliationService;
import com.travel2go.backend.service.ReconciliationSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReconciliationController.class)
@org.springframework.context.annotation.Import(com.travel2go.backend.security.SecurityConfig.class)
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReconciliationService reconciliationService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void reconciliation_requiresAdmin_returns403ForNonAdmin() throws Exception {
        when(jwtUtil.extractUsername(any())).thenReturn("test-user");
        when(jwtUtil.extractRoles(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/payments/reconciliation")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconciliation_returnsSummaryForAdmin() throws Exception {
        when(jwtUtil.extractUsername(any())).thenReturn("admin-user");
        when(jwtUtil.extractRoles(any())).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(reconciliationService.getSummary())
                .thenReturn(new ReconciliationSummary(500000L, 150000L, 350000L, List.of()));

        mockMvc.perform(get("/api/payments/reconciliation")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapturedPaise").value(500000))
                .andExpect(jsonPath("$.totalRefundedPaise").value(150000))
                .andExpect(jsonPath("$.totalSettledPaise").value(350000));
    }
}
