package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.dto.BookingEvent;
import com.travel2go.backend.model.Booking;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingEventProcessorTest {

    @Mock
    private Receiver receiver;

    @Mock
    private Sender sender;

    @Mock
    private ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> reactiveValueOperations;

    @Mock
    private org.springframework.amqp.core.AmqpAdmin amqpAdmin;

    @Mock
    private org.springframework.amqp.core.Queue bookingQueue;

    @Mock
    private org.springframework.amqp.core.DirectExchange bookingExchange;

    @Mock
    private org.springframework.amqp.core.Binding bookingBinding;

    private ObjectMapper objectMapper = new ObjectMapper();

    private BookingEventProcessor bookingEventProcessor;

    @BeforeEach
    public void setUp() {
        lenient().when(reactiveRedisTemplate.opsForValue()).thenReturn(reactiveValueOperations);
        
        bookingEventProcessor = new BookingEventProcessor(
                receiver,
                sender,
                reactiveRedisTemplate,
                objectMapper,
                amqpAdmin,
                bookingQueue,
                bookingExchange,
                bookingBinding
        );
        
        org.springframework.test.util.ReflectionTestUtils.setField(bookingEventProcessor, "queueName", "booking.events.queue");
        org.springframework.test.util.ReflectionTestUtils.setField(bookingEventProcessor, "exchange", "booking.exchange");
        org.springframework.test.util.ReflectionTestUtils.setField(bookingEventProcessor, "processedRoutingKey", "booking.processed");
    }

    @Test
    public void testHandleEvent_NewEvent_Success() {
        Booking booking = new Booking();
        booking.setId("booking-123");
        booking.setEmail("user@example.com");
        booking.setStatus("PENDING");

        BookingEvent event = BookingEvent.builder()
                .eventId("event-789")
                .eventType("BOOKING_INITIATED")
                .timestamp(System.currentTimeMillis())
                .booking(booking)
                .build();

        String idempotencyKey = "event:idempotency:event-789";
        String bookingCacheKey = "booking:cache:booking-123";

        when(reactiveValueOperations.setIfAbsent(eq(idempotencyKey), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        when(reactiveValueOperations.set(eq(bookingCacheKey), any(Booking.class), any(Duration.class)))
                .thenReturn(Mono.just(true));

        when(sender.send(any())).thenReturn(Mono.empty());

        StepVerifier.create(bookingEventProcessor.handleEvent(event))
                .verifyComplete();

        verify(reactiveValueOperations).setIfAbsent(eq(idempotencyKey), eq("PROCESSED"), any(Duration.class));
        verify(reactiveValueOperations).set(eq(bookingCacheKey), any(Booking.class), any(Duration.class));
        verify(sender).send(any());
    }

    @Test
    public void testHandleEvent_DuplicateEvent_Skipped() {
        Booking booking = new Booking();
        booking.setId("booking-123");

        BookingEvent event = BookingEvent.builder()
                .eventId("event-789")
                .booking(booking)
                .build();

        String idempotencyKey = "event:idempotency:event-789";

        when(reactiveValueOperations.setIfAbsent(eq(idempotencyKey), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(bookingEventProcessor.handleEvent(event))
                .verifyComplete();

        verify(reactiveValueOperations).setIfAbsent(eq(idempotencyKey), eq("PROCESSED"), any(Duration.class));
        verify(reactiveValueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(sender, never()).send(any());
    }
}
