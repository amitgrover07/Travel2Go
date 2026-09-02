package com.travel2go.backend.client;

import com.travel2go.backend.dto.LegBookingRequest;
import com.travel2go.backend.dto.LegBookingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "booking-service", url = "${BOOKING_SERVICE_URL:http://localhost:8083}",
        configuration = com.travel2go.backend.config.FeignConfig.class)
public interface BookingClient {

    @PostMapping("/api/leg-bookings")
    LegBookingResponse createLegBooking(@RequestBody LegBookingRequest request);
}
