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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegConfirmedConsumerTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationClient notificationClient;

    private LegConfirmedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LegConfirmedConsumer(bookingRepository, notificationClient);
        lenient().when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void onLegConfirmed_confirmsPendingBooking() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository).save(argThat(b ->
                "CONFIRMED".equals(b.getStatus()) && "pay_1".equals(b.getProviderPaymentId()) && b.getConfirmedAt() != null));
    }

    @Test
    void onLegConfirmed_duplicateDeliveryConfirmsOnlyOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));
        pending.setStatus("CONFIRMED");
        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository, times(1)).save(any());
    }

    @Test
    void onLegConfirmed_amountMismatchDoesNotConfirm() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 999L));

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void onLegConfirmed_unknownLegIdThrows() {
        when(bookingRepository.findByLegId("leg-unknown")).thenReturn(Flux.empty());

        assertThatThrownBy(() ->
                consumer.onLegConfirmed(new LegConfirmedEvent("leg-unknown", "pay_1", 150000L)))
                .isInstanceOf(IllegalStateException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void onLegConfirmed_prefersActiveBookingOverStaleRejectedOne() {
        Booking rejected = Booking.builder().id("b0").legId("leg-1").status("REJECTED").amountPaise(150000L).build();
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L).build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(rejected, pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(bookingRepository).save(argThat(b ->
                "b1".equals(b.getId()) && "CONFIRMED".equals(b.getStatus())));
    }

    @Test
    void onLegConfirmed_unexpectedExceptionPropagates() {
        when(bookingRepository.findByLegId("leg-1")).thenThrow(new RuntimeException("transient Firestore error"));

        assertThatThrownBy(() ->
                consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("transient Firestore error");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void onLegConfirmed_confirmingBookingSendsNotification() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L)
                .email("traveller@example.com").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(notificationClient).sendLegBookingConfirmation(org.mockito.ArgumentMatchers.argThat(req ->
                "traveller@example.com".equals(req.email) && req.amountPaise == 150000L));
    }

    @Test
    void onLegConfirmed_duplicateDeliverySendsNotificationOnlyOnce() {
        Booking pending = Booking.builder().id("b1").legId("leg-1").status("PENDING").amountPaise(150000L)
                .email("traveller@example.com").build();
        when(bookingRepository.findByLegId("leg-1")).thenReturn(Flux.just(pending));

        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));
        pending.setStatus("CONFIRMED");
        consumer.onLegConfirmed(new LegConfirmedEvent("leg-1", "pay_1", 150000L));

        verify(notificationClient, times(1)).sendLegBookingConfirmation(any());
    }
}
