package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.PaymentResult;
import com.travel2go.backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, paymentProvider, quoteTokenService);
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void charge_succeedsAndNeverAddsAFee() {
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.charge("leg-1", 150000L, "UPI"))
                .thenReturn(new PaymentResult("SUCCESS", "SANDBOX-abc123"));

        Payment result = paymentService.charge("leg-1", 150000L, "UPI", "valid-token");

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        assertThat(result.getProviderRef()).isEqualTo("SANDBOX-abc123");
    }

    @Test
    void charge_rejectsWhenQuoteTokenInvalid_withoutCallingProvider() {
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        Payment result = paymentService.charge("leg-1", 150000L, "UPI", "bad-token");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        verify(paymentProvider, never()).charge(any(), anyLong(), any());
    }
}
