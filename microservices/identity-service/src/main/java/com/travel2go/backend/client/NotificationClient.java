package com.travel2go.backend.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "notification-service", url = "${NOTIFICATION_SERVICE_URL:http://localhost:8085}")
public interface NotificationClient {

    @PostMapping("/api/notifications/email/otp")
    void sendEmailOtp(@RequestParam("email") String email, @RequestParam("code") String code);

    @PostMapping("/api/notifications/sms/otp")
    void sendSmsOtp(@RequestParam("phone") String phone, @RequestParam("code") String code);
}
