package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.dto.BookingEvent;
import com.travel2go.backend.model.Booking;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingEventProcessor {

    private final Receiver receiver;
    private final Sender sender;
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.queue}")
    private String queueName;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.processed-routing-key}")
    private String processedRoutingKey;

    private Disposable subscription;

    @PostConstruct
    public void startListening() {
        log.info("Starting reactive RabbitMQ consumer on queue: {}", queueName);
        
        subscription = receiver.consumeManualAck(queueName)
                .flatMap(delivery -> processDelivery(delivery)
                        .doOnSuccess(v -> delivery.ack())
                        .doOnError(e -> {
                            log.error("Error processing delivery, nacking message: {}", e.getMessage());
                            delivery.nack(false); // do not requeue
                        })
                        .onErrorResume(e -> Mono.empty())
                )
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<Void> processDelivery(reactor.rabbitmq.AcknowledgableDelivery delivery) {
        return Mono.fromCallable(() -> {
            byte[] body = delivery.getBody();
            return objectMapper.readValue(body, BookingEvent.class);
        })
        .flatMap(event -> {
            log.info("Received booking event: {} for booking ID: {}", event.getEventId(), event.getBooking().getId());
            return handleEvent(event);
        })
        .then();
    }

    public Mono<Void> handleEvent(BookingEvent event) {
        String idempotencyKey = "event:idempotency:" + event.getEventId();
        String bookingCacheKey = "booking:cache:" + event.getBooking().getId();

        // 1. Idempotency Check via Redis (Non-blocking setIfAbsent)
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "PROCESSED", Duration.ofHours(24))
                .flatMap(isNewEvent -> {
                    if (Boolean.TRUE.equals(isNewEvent)) {
                        log.info("Processing new booking event: {}", event.getEventId());
                        return processBooking(event.getBooking())
                                // 2. Cache processed booking in Redis (Non-blocking write)
                                .flatMap(processedBooking -> reactiveRedisTemplate.opsForValue()
                                        .set(bookingCacheKey, processedBooking, Duration.ofMinutes(30))
                                        .thenReturn(processedBooking)
                                )
                                // 3. Publish result back to RabbitMQ (Non-blocking publish)
                                .flatMap(processedBooking -> publishProcessedEvent(event.getEventId(), processedBooking));
                    } else {
                        log.warn("Duplicate event detected! Skipping event ID: {}", event.getEventId());
                        return Mono.empty();
                    }
                })
                .then();
    }

    private Mono<Booking> processBooking(Booking booking) {
        // Simulating reactive processing business logic (e.g. updating status)
        return Mono.just(booking)
                .map(b -> {
                    b.setStatus("PROCESSED_BY_CLOUD_RUN");
                    return b;
                })
                .delayElement(Duration.ofMillis(100)); // Simulate async processing latency
    }

    private Mono<Void> publishProcessedEvent(String originalEventId, Booking booking) {
        return Mono.defer(() -> {
            try {
                BookingEvent processedEvent = BookingEvent.builder()
                        .eventId(java.util.UUID.randomUUID().toString())
                        .eventType("BOOKING_PROCESSED")
                        .timestamp(System.currentTimeMillis())
                        .booking(booking)
                        .build();

                byte[] body = objectMapper.writeValueAsBytes(processedEvent);
                OutboundMessage message = new OutboundMessage(exchange, processedRoutingKey, body);
                
                log.info("Publishing processed booking event to routing key: {}", processedRoutingKey);
                return sender.send(Mono.just(message)).then();
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    @PreDestroy
    public void cleanup() {
        if (subscription != null) {
            subscription.dispose();
        }
        try {
            receiver.close();
        } catch (Exception ignored) {}
        try {
            sender.close();
        } catch (Exception ignored) {}
    }
}
