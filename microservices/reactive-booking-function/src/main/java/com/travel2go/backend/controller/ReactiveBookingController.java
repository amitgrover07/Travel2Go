package com.travel2go.backend.controller;

import com.travel2go.backend.dto.BookingEvent;
import com.travel2go.backend.service.BookingEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/reactive")
@RequiredArgsConstructor
@Slf4j
public class ReactiveBookingController {

    private final BookingEventProcessor bookingEventProcessor;

    @PostMapping("/process")
    public Mono<ResponseEntity<String>> triggerProcessBooking(@RequestBody BookingEvent event) {
        log.info("HTTP Trigger: Processing booking event ID: {}", event.getEventId());
        if (event.getEventId() == null || event.getBooking() == null) {
            return Mono.just(ResponseEntity.badRequest().body("Missing eventId or booking payload"));
        }

        return bookingEventProcessor.handleEvent(event)
                .thenReturn(ResponseEntity.ok("Event processed successfully via HTTP trigger"))
                .onErrorResume(e -> {
                    log.error("Failed to process event via HTTP trigger: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body("Processing failed: " + e.getMessage()));
                });
    }
}
