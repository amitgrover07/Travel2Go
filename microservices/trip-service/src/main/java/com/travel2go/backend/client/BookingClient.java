package com.travel2go.backend.client;

import org.springframework.cloud.openfeign.FeignClient;

// Full leg-booking method added in Task 5, once booking-service exposes POST /api/leg-bookings.
@FeignClient(name = "booking-service", url = "${BOOKING_SERVICE_URL:http://localhost:8083}",
        configuration = com.travel2go.backend.config.FeignConfig.class)
public interface BookingClient {
}
