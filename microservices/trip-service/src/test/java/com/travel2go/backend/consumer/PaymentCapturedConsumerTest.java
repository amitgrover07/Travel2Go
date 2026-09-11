package com.travel2go.backend.consumer;

import com.travel2go.backend.model.Leg;
import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.service.LegConfirmedEvent;
import com.travel2go.backend.service.TripEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCapturedConsumerTest {

    @Mock private LegRepository legRepository;
    @Mock private TripEventPublisher tripEventPublisher;

    private PaymentCapturedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentCapturedConsumer(legRepository, tripEventPublisher);
        lenient().when(legRepository.save(any(Leg.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void onPaymentCaptured_confirmsPendingLeg() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").pricePaise(150000L).build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(legRepository).save(argThat(l -> "CONFIRMED".equals(l.getStatus())));
    }

    @Test
    void onPaymentCaptured_confirmingLegPublishesLegConfirmed() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").pricePaise(150000L).build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(tripEventPublisher).publish("leg.confirmed",
                new LegConfirmedEvent("leg-1", "pay_1", 150000L));
    }

    @Test
    void onPaymentCaptured_amountMismatchDoesNotConfirm() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").pricePaise(150000L).build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 999L));

        verify(legRepository, never()).save(any());
        verify(tripEventPublisher, never()).publish(any(), any());
    }

    @Test
    void onPaymentCaptured_duplicateDeliveryIsNoOp() {
        Leg leg = Leg.builder().id("leg-1").status("PENDING").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));
        leg.setStatus("CONFIRMED");
        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(legRepository, times(1)).save(any());
        verify(tripEventPublisher, times(1)).publish(any(), any());
    }

    @Test
    void onPaymentCaptured_unknownLegThrows() {
        when(legRepository.findById("leg-unknown")).thenReturn(Mono.empty());

        assertThatThrownBy(() ->
                consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-unknown", "pay_1", 150000L)))
                .isInstanceOf(IllegalStateException.class);

        verify(legRepository, never()).save(any());
        verify(tripEventPublisher, never()).publish(any(), any());
    }

    @Test
    void onPaymentCaptured_unexpectedExceptionPropagates() {
        when(legRepository.findById("leg-1")).thenThrow(new RuntimeException("transient Firestore error"));

        assertThatThrownBy(() ->
                consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("transient Firestore error");

        verify(legRepository, never()).save(any());
    }
}
