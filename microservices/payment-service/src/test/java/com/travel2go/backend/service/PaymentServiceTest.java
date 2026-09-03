package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEvent;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;
    @Mock private PaymentEventPublisher eventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, processedWebhookEventRepository, paymentProvider, quoteTokenService, eventPublisher);
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(processedWebhookEventRepository.findById(any(String.class))).thenReturn(Mono.empty());
        lenient().when(paymentRepository.findByBookingRef(any())).thenReturn(Flux.empty());
    }

    private Payment createdPayment() {
        return Payment.builder()
                .id("p1")
                .bookingRef("leg-1")
                .method("UPI")
                .status("CREATED")
                .amountPaise(150000L)
                .feePaise(0L)
                .providerRef("order_1")
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();
    }

    @Test
    void createOrder_succeedsAndNeverAddsAFee() {
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_1", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1");

        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        assertThat(result.getProviderRef()).isEqualTo("order_1");
    }

    @Test
    void createOrder_rejectsWhenQuoteTokenInvalid_withoutCallingProvider() {
        when(quoteTokenService.isValid("bad-token", "leg-1", 150000L)).thenReturn(false);

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "bad-token", "user-1");

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getFeePaise()).isEqualTo(0L);
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
    }

    @Test
    void createOrder_returnsExistingPaymentWhenAlreadyCreatedForBookingRef() {
        Payment existing = createdPayment();
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(existing));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-1");

        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo("CREATED");
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
    }

    @Test
    void createOrder_returnsExistingPaymentWhenAlreadyCapturedForBookingRef() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(captured));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-1");

        assertThat(result).isSameAs(captured);
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
    }

    @Test
    void createOrder_ignoresRejectedPaymentAndCreatesNewOrder() {
        Payment rejected = createdPayment();
        rejected.setStatus("REJECTED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(rejected));
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_2", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1");

        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getProviderRef()).isEqualTo("order_2");
        verify(paymentProvider).createOrder("leg-1", 150000L, "UPI");
    }

    @Test
    void applyWebhook_capturedTransitionsPaymentAndPublishesEvent() {
        Payment payment = createdPayment();
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(payment));
        when(processedWebhookEventRepository.findById("pay_1")).thenReturn(Mono.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository).save(argThat(p -> "CAPTURED".equals(p.getStatus()) && "pay_1".equals(p.getProviderPaymentId())));
        verify(eventPublisher).publish(eq("payment.captured"),
                eq(new PaymentCapturedEvent("leg-1", "pay_1", 150000L)));
    }

    @Test
    void applyWebhook_duplicateCapturedIsIdempotent_publishesEventOnlyOnce() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(createdPayment()));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.findById("pay_1"))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(ProcessedWebhookEvent.builder().id("pay_1").processedAt(new Date()).build()));
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        paymentService.applyWebhook("{}".getBytes(), Map.of());
        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }

    @Test
    void applyWebhook_amountMismatchDoesNotCapture() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(createdPayment()));
        when(processedWebhookEventRepository.findById("pay_1")).thenReturn(Mono.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 999L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void applyWebhook_failedAfterCapturedIsIgnored() {
        Payment alreadyCaptured = createdPayment();
        alreadyCaptured.setStatus("CAPTURED");
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Flux.just(alreadyCaptured));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.FAILED, "order_1", "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void applyWebhook_nullProviderOrderIdIsIgnored() {
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, null, "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).findByProviderRef(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void applyWebhook_unknownOrderIsIgnored() {
        when(paymentRepository.findByProviderRef("order_unknown")).thenReturn(Flux.empty());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_unknown", "pay_1", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refund_transitionsCapturedToRefunded() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(captured));
        when(paymentProvider.refund("pay_1", 150000L)).thenReturn(new RefundResult("SUCCESS", "rfnd_1"));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(eventPublisher).publish(eq("payment.refunded"),
                eq(new PaymentRefundedEvent("leg-1", "rfnd_1", 150000L)));
    }

    @Test
    void refund_isIdempotentOnAlreadyRefunded() {
        Payment refunded = createdPayment();
        refunded.setStatus("REFUNDED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(refunded));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(paymentProvider, never()).refund(any(), anyLong());
        verify(eventPublisher, never()).publish(eq("payment.refunded"), any());
    }

    @Test
    void getStatus_prefersCapturedPaymentOverNewerRejectedPayment() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        captured.setCreatedAt(new Date(1000L));

        Payment newerRejected = createdPayment();
        newerRejected.setId("p2");
        newerRejected.setStatus("REJECTED");
        newerRejected.setCreatedAt(new Date(2000L));

        // Newer payment listed first, to prove "newest" isn't blindly picked.
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(newerRejected, captured));

        Payment result = paymentService.getStatus("leg-1", "leg-1", true);

        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(result.getId()).isEqualTo("p1");
    }

    @Test
    void createOrder_capturesOwnerUserId() {
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_1", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1");

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void getStatus_returnsForOwner() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(
                Flux.just(Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        Payment result = paymentService.getStatus("leg-1", "user-1", false);

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void getStatus_throwsForNonOwnerNonAdmin() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(
                Flux.just(Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        assertThatThrownBy(() -> paymentService.getStatus("leg-1", "user-2", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getStatus_allowsAdminForAnyOwner() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(
                Flux.just(Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        Payment result = paymentService.getStatus("leg-1", "admin-user", true);

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void refund_prefersCapturedPaymentOverNewerRejectedPayment() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        captured.setCreatedAt(new Date(1000L));

        Payment newerRejected = createdPayment();
        newerRejected.setId("p2");
        newerRejected.setStatus("REJECTED");
        newerRejected.setCreatedAt(new Date(2000L));

        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(Flux.just(newerRejected, captured));
        when(paymentProvider.refund("pay_1", 150000L)).thenReturn(new RefundResult("SUCCESS", "rfnd_1"));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        assertThat(result.getId()).isEqualTo("p1");
        verify(paymentProvider).refund("pay_1", 150000L);
    }
}
