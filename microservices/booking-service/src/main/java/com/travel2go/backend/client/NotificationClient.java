package com.travel2go.backend.client;

import com.travel2go.backend.dto.BookingRequest;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.model.HolidayPackage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service", url = "${NOTIFICATION_SERVICE_URL:http://localhost:8085}")
public interface NotificationClient {

    @PostMapping("/api/notifications/send-confirmation")
    void sendBookingConfirmation(@RequestBody NotificationRequest request);
    
    // We create a local DTO to hold all data
    public static class NotificationRequest {
        public BookingRequest bookingRequest;
        public HolidayPackage holidayPackage;
        public GlobalSettings globalSettings;
        public Booking booking;
        
        public NotificationRequest(BookingRequest req, HolidayPackage pkg, GlobalSettings settings, Booking bk) {
            this.bookingRequest = req;
            this.holidayPackage = pkg;
            this.globalSettings = settings;
            this.booking = bk;
        }
        
        public NotificationRequest() {}
    }
}
