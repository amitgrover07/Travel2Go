package com.travel2go.backend.controller;

import com.travel2go.backend.dto.BookingRequest;
import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.GlobalSettingsRepository;
import com.travel2go.backend.repository.HolidayPackageRepository;
import com.travel2go.backend.service.EmailService;
import com.travel2go.backend.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final EmailService emailService;
    private final PdfService pdfService;
    private final HolidayPackageRepository packageRepository;
    private final GlobalSettingsRepository settingsRepository;

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody BookingRequest request) {
        try {
            // Fetch package details for the PDF
            HolidayPackage pkg = packageRepository.findById(request.getPackageId()).block();
            GlobalSettings settings = settingsRepository.findById("global").block();

            byte[] pdfBytes = null;
            if (pkg != null) {
                pdfBytes = pdfService.generatePackagePdf(pkg, settings);
            }

            // Send confirmation email with PDF attachment
            emailService.sendBookingConfirmation(request, pdfBytes);
            
            return ResponseEntity.ok(Map.of("message", "Booking submitted successfully! A confirmation email with the itinerary has been sent."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process booking: " + e.getMessage()));
        }
    }
}
