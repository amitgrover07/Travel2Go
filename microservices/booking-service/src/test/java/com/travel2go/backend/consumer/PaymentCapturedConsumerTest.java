package com.travel2go.backend.consumer;

import com.travel2go.backend.client.NotificationClient;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCapturedConsumerTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationClient notificationClient;

    private PaymentCapturedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentCapturedConsumer(bookingRepository, notificationClient);
        lenient().when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void onPaymentCaptured_confirmsPendingBookingAndNotifiesOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository).save(argThat(b ->
                "CONFIRMED".equals(b.getStatus()) && "pay_1".equals(b.getProviderPaymentId()) && b.getConfirmedAt() != null));
        verify(notificationClient, times(1)).sendBookingConfirmation(any());
    }

    @Test
    void onPaymentCaptured_duplicateDeliveryConfirmsOnlyOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));
        pending.setStatus("CONFIRMED");
        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(notificationClient, times(1)).sendBookingConfirmation(any());
    }

    @Test
    void onPaymentCaptured_amountMismatchDoesNotConfirm() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 999L));

        verify(bookingRepository, never()).save(any());
        verify(notificationClient, never()).sendBookingConfirmation(any());
    }

    @Test
    void onPaymentCaptured_unknownLegIdDoesNotThrow() {
        when(bookingRepository.findByLegId("leg-unknown")).thenReturn(Flux.empty());

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-unknown", "pay_1", 150000L));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void onPaymentCaptured_prefersActiveBookingOverStaleRejectedOne() {
        Booking rejected = Booking.builder().id("b0").legId("leg-1").status("REJECTED").amountPaise(150000L).build();
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(rejected, pending));

        consumer.onPaymentCaptured(new PaymentCapturedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository).save(argThat(b ->
                "b1".equals(b.getId()) && "CONFIRMED".equals(b.getStatus())));
        verify(notificationClient, times(1)).sendBookingConfirmation(any());
    }
}
