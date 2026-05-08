package com.travel2go.backend.controller;

import com.travel2go.backend.dto.BookingRequest;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.BookingRepository;
import com.travel2go.backend.repository.GlobalSettingsRepository;
import com.travel2go.backend.repository.HolidayPackageRepository;
import com.travel2go.backend.service.EmailService;
import com.travel2go.backend.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final EmailService emailService;
    private final PdfService pdfService;
    private final HolidayPackageRepository packageRepository;
    private final GlobalSettingsRepository settingsRepository;
    private final BookingRepository bookingRepository;

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody BookingRequest request) {
        try {
            // Validate required fields
            if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "First name is required"));
            }
            if (request.getEmail() == null || !request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid email is required"));
            }
            if (request.getPhone() == null || request.getPhone().trim().length() < 10) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid phone number is required (min 10 characters)"));
            }
            if (request.getLocation() == null || request.getLocation().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Location is required"));
            }

            // Save the booking to the database
            Booking booking = Booking.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .location(request.getLocation())
                .packageId(request.getPackageId())
                .packageTitle(request.getPackageTitle())
                .bookingDate(new java.util.Date())
                .status("CONFIRMED")
                .build();
            
            bookingRepository.save(booking).block();

            // Fetch package details for the PDF
            HolidayPackage pkg = packageRepository.findById(request.getPackageId()).block();
            GlobalSettings settings = settingsRepository.findById("global").block();

            byte[] pdfBytes = null;
            if (pkg != null) {
                pdfBytes = pdfService.generatePackagePdf(pkg, settings, booking);
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
