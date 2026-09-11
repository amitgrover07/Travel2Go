package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.OutboxEntry;
import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.CreatedOrder;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.OutboxRepository;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ProcessedWebhookEventRepository processedWebhookEventRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private PaymentProvider paymentProvider;
    @Mock private QuoteTokenService quoteTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository, processedWebhookEventRepository, refundRepository, outboxRepository,
                paymentProvider, quoteTokenService, objectMapper);
        lenient().when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(refundRepository.save(any(com.travel2go.backend.model.Refund.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxRepository.save(any(OutboxEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(processedWebhookEventRepository.findById(any(String.class))).thenReturn(Optional.empty());
        lenient().when(paymentRepository.findByBookingRef(any())).thenReturn(List.of());
    }

    private Payment createdPayment() {
        return Payment.builder()
                .bookingRef("leg-1")
                .method("UPI")
                .status("CREATED")
                .amountPaise(150000L)
                .feePaise(0L)
                .providerRef("order_1")
                .ownerUserId("user-1")
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();
    }

    private boolean outboxEntryMatches(OutboxEntry entry, String expectedType, Object expectedEvent) {
        if (!expectedType.equals(entry.getType())) {
            return false;
        }
        try {
            Object actual = objectMapper.readValue(entry.getPayload(), expectedEvent.getClass());
            return expectedEvent.equals(actual);
        } catch (Exception e) {
            return false;
        }
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
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(existing));

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
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(captured));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-1");

        assertThat(result).isSameAs(captured);
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
    }

    @Test
    void createOrder_differentOwnerCreatedThrowsConflictAndDoesNotPersist() {
        Payment existing = createdPayment();
        existing.setOwnerUserId("user-1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(existing));

        assertThatThrownBy(() ->
                paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-2"))
                .isInstanceOf(PaymentConflictException.class);

        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrder_differentOwnerCapturedThrowsConflictAndDoesNotPersist() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        captured.setOwnerUserId("user-1");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(captured));

        assertThatThrownBy(() ->
                paymentService.createOrder("leg-1", 150000L, "UPI", "any-token", "user-2"))
                .isInstanceOf(PaymentConflictException.class);

        verify(paymentProvider, never()).createOrder(any(), anyLong(), any());
        verify(quoteTokenService, never()).isValid(any(), any(), anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrder_ignoresRejectedPaymentAndCreatesNewOrder() {
        Payment rejected = createdPayment();
        rejected.setStatus("REJECTED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(rejected));
        when(quoteTokenService.isValid("valid-token", "leg-1", 150000L)).thenReturn(true);
        when(paymentProvider.createOrder("leg-1", 150000L, "UPI"))
                .thenReturn(new CreatedOrder("order_2", "key_1", 150000L, "INR"));

        Payment result = paymentService.createOrder("leg-1", 150000L, "UPI", "valid-token", "user-1");

        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getProviderRef()).isEqualTo("order_2");
        verify(paymentProvider).createOrder("leg-1", 150000L, "UPI");
    }

    @Test
    void applyWebhook_capturedTransitionsPaymentAndWritesOutboxEntry() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(1);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository).markCaptured(payment.getId(), "pay_1");
        PaymentCapturedEvent expected = new PaymentCapturedEvent("leg-1", "pay_1", 150000L);
        verify(outboxRepository).save(argThat(entry ->
                outboxEntryMatches(entry, "payment.captured", expected)
                        && "leg-1".equals(entry.getAggregateId())));
    }

    @Test
    void applyWebhook_duplicateCapturedIsIdempotent_writesOutboxEntryOnlyOnce() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1, 0);
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(1);

        paymentService.applyWebhook("{}".getBytes(), Map.of());
        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(outboxRepository, times(1)).save(any(OutboxEntry.class));
    }

    @Test
    void applyWebhook_amountMismatchDoesNotCapture() {
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(createdPayment()));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 999L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).markCaptured(any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void applyWebhook_duplicateEventIdIsNoOpEvenIfPaymentStillCreated() {
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(0);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(paymentRepository, never()).findByProviderRef(any());
        verify(paymentRepository, never()).markCaptured(any(), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void applyWebhook_captureRaceLoserIsNoOp_whenMarkCapturedAffectsZeroRows() {
        Payment payment = createdPayment();
        payment.setId(java.util.UUID.randomUUID());
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_1", "pay_1", 150000L));
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.markCaptured(payment.getId(), "pay_1")).thenReturn(0);

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void applyWebhook_failedAfterCapturedIsIgnored() {
        Payment alreadyCaptured = createdPayment();
        alreadyCaptured.setStatus("CAPTURED");
        when(paymentRepository.findByProviderRef("order_1")).thenReturn(Optional.of(alreadyCaptured));
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
        when(paymentRepository.findByProviderRef("order_unknown")).thenReturn(Optional.empty());
        when(processedWebhookEventRepository.recordIfNew("pay_1")).thenReturn(1);
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
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(captured));
        when(paymentProvider.refund("pay_1", 150000L)).thenReturn(new RefundResult("SUCCESS", "rfnd_1"));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        PaymentRefundedEvent expected = new PaymentRefundedEvent("leg-1", "rfnd_1", 150000L);
        verify(outboxRepository).save(argThat(entry -> outboxEntryMatches(entry, "payment.refunded", expected)));
    }

    @Test
    void refund_isIdempotentOnAlreadyRefunded() {
        Payment refunded = createdPayment();
        refunded.setStatus("REFUNDED");
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(refunded));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        verify(paymentProvider, never()).refund(any(), anyLong());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void getStatus_prefersCapturedPaymentOverNewerRejectedPayment() {
        Payment captured = createdPayment();
        captured.setStatus("CAPTURED");
        captured.setProviderPaymentId("pay_1");
        captured.setCreatedAt(new Date(1000L));

        Payment newerRejected = createdPayment();
        newerRejected.setStatus("REJECTED");
        newerRejected.setCreatedAt(new Date(2000L));

        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(newerRejected, captured));

        Payment result = paymentService.getStatus("leg-1", "leg-1", true);

        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(result.getProviderPaymentId()).isEqualTo("pay_1");
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
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(
                Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        Payment result = paymentService.getStatus("leg-1", "user-1", false);

        assertThat(result.getOwnerUserId()).isEqualTo("user-1");
    }

    @Test
    void getStatus_throwsForNonOwnerNonAdmin() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(
                Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
                        .amountPaise(150000L).createdAt(new Date()).build()));

        assertThatThrownBy(() -> paymentService.getStatus("leg-1", "user-2", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getStatus_allowsAdminForAnyOwner() {
        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(
                Payment.builder().bookingRef("leg-1").status("CREATED").ownerUserId("user-1")
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
        newerRejected.setStatus("REJECTED");
        newerRejected.setCreatedAt(new Date(2000L));

        when(paymentRepository.findByBookingRef("leg-1")).thenReturn(List.of(newerRejected, captured));
        when(paymentProvider.refund("pay_1", 150000L)).thenReturn(new RefundResult("SUCCESS", "rfnd_1"));

        Payment result = paymentService.refund("leg-1");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        assertThat(result.getProviderPaymentId()).isEqualTo("pay_1");
        verify(paymentProvider).refund("pay_1", 150000L);
    }
}
