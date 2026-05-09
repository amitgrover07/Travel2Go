package com.travel2go.backend.controller;

import com.travel2go.backend.dto.BookingRequest;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.service.EmailService;
import com.travel2go.backend.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final EmailService emailService;
    private final PdfService pdfService;
    private final com.travel2go.backend.service.SmsService smsService;

    @PostMapping("/send-confirmation")
    public ResponseEntity<Void> sendBookingConfirmation(@RequestBody NotificationRequest request) {
        try {
            byte[] pdfBytes = null;
            if (request.holidayPackage != null) {
                pdfBytes = pdfService.generatePackagePdf(
                        request.holidayPackage, 
                        request.globalSettings, 
                        request.booking
                );
            }
            emailService.sendBookingConfirmation(request.bookingRequest, pdfBytes);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/email/otp")
    public ResponseEntity<Void> sendEmailOtp(@RequestParam String email, @RequestParam String code) {
        try {
            emailService.sendVerificationCode(email, code);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/sms/otp")
    public ResponseEntity<Void> sendSmsOtp(@RequestParam String phone, @RequestParam String code) {
        try {
            smsService.sendVerificationCode(phone, code);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class NotificationRequest {
        public BookingRequest bookingRequest;
        public HolidayPackage holidayPackage;
        public GlobalSettings globalSettings;
        public Booking booking;
    }
}
