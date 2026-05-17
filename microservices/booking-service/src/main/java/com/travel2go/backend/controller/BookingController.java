package com.travel2go.backend.controller;

import com.travel2go.backend.dto.BookingRequest;
import com.travel2go.backend.model.Booking;
import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.model.Lead;
import com.travel2go.backend.repository.BookingRepository;
import com.travel2go.backend.repository.LeadRepository;
import com.travel2go.backend.client.PackageClient;
import com.travel2go.backend.client.SettingsClient;
import com.travel2go.backend.client.NotificationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final NotificationClient notificationClient;
    private final PackageClient packageClient;
    private final SettingsClient settingsClient;
    private final BookingRepository bookingRepository;
    private final LeadRepository leadRepository;

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

            // Fetch package details for the PDF via Feign
            HolidayPackage pkg;
            if (request.isCustom()) {
                com.travel2go.backend.model.CustomPackage cp = packageClient.getCustomPackageById(request.getPackageId());
                // Map CustomPackage to HolidayPackage for the notification service
                pkg = HolidayPackage.builder()
                    .id(cp.getId())
                    .packageCode(cp.getPackageCode())
                    .title(cp.getTitle())
                    .destination(cp.getDestination())
                    .status(cp.getStatus())
                    .packageType(cp.getPackageType())
                    .overview(cp.getOverview())
                    .specialNotes(cp.getSpecialNotes())
                    .duration(cp.getDuration())
                    .pricing(cp.getPricing())
                    .media(cp.getMedia())
                    .inclusions(cp.getInclusions())
                    .exclusions(cp.getExclusions())
                    .itinerary(cp.getItinerary())
                    .audit(cp.getAudit())
                    .build();
            } else {
                pkg = packageClient.getPackageById(request.getPackageId());
            }

            // Create a Lead when package is sent
            Lead lead = Lead.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .location(request.getLocation())
                .packageId(pkg.getId())
                .packageTitle(pkg.getTitle())
                .packageCode(pkg.getPackageCode())
                .basePrice(pkg.getPricing() != null ? pkg.getPricing().getBasePrice() : 0.0)
                .discountPercentage(pkg.getPricing() != null ? pkg.getPricing().getDiscountPercentage() : 0.0)
                .finalPrice(pkg.getPricing() != null ? pkg.getPricing().getFinalPrice() : 0.0)
                .leadDate(new java.util.Date())
                .status("NEW")
                .source("EMAIL_SENT")
                .bestTimeToReach(request.getBestTimeToReach())
                .notes("")
                .build();
                
            leadRepository.save(lead).block();

            GlobalSettings settings = settingsClient.getSettingsById("global");

            // Send notification request to Notification Service
            NotificationClient.NotificationRequest notifReq = new NotificationClient.NotificationRequest(request, pkg, settings, booking);
            notificationClient.sendBookingConfirmation(notifReq);
            
            return ResponseEntity.ok(Map.of("message", "Package sent successfully! Check email for the PDF itinerary."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process booking: " + e.getMessage()));
        }
    }
}
