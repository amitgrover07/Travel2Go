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
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import com.travel2go.backend.model.LeadAuditLog;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final NotificationClient notificationClient;
    private final PackageClient packageClient;
    private final SettingsClient settingsClient;
    private final BookingRepository bookingRepository;
    private final LeadRepository leadRepository;
    private final RabbitTemplate rabbitTemplate;

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

            // Publish event to RabbitMQ
            try {
                java.util.Map<String, Object> eventPayload = java.util.Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "BOOKING_INITIATED",
                    "timestamp", System.currentTimeMillis(),
                    "booking", booking
                );
                rabbitTemplate.convertAndSend("booking.exchange", "booking.initiated", eventPayload);
                System.out.println("Published booking event to RabbitMQ for booking ID: " + booking.getId());
            } catch (Exception amqpEx) {
                System.err.println("Failed to publish booking event to RabbitMQ: " + amqpEx.getMessage());
            }

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

            // Create or update Lead when package is sent
            Lead lead;
            if (request.getLeadId() != null && !request.getLeadId().trim().isEmpty()) {
                lead = leadRepository.findById(request.getLeadId()).block();
                if (lead != null) {
                    String oldCode = lead.getPackageCode() != null ? lead.getPackageCode() : "NONE";
                    lead.setMailSentCount(lead.getMailSentCount() != null ? lead.getMailSentCount() + 1 : 1);
                    
                    // Update lead package details
                    lead.setPackageId(pkg.getId());
                    lead.setPackageTitle(pkg.getTitle());
                    lead.setPackageCode(pkg.getPackageCode());
                    lead.setBasePrice(pkg.getPricing() != null ? pkg.getPricing().getBasePrice() : 0.0);
                    lead.setDiscountPercentage(pkg.getPricing() != null ? pkg.getPricing().getDiscountPercentage() : 0.0);
                    lead.setFinalPrice(pkg.getPricing() != null ? pkg.getPricing().getFinalPrice() : 0.0);

                    if (lead.getAuditLogs() == null) lead.setAuditLogs(new ArrayList<>());
                    lead.getAuditLogs().add(LeadAuditLog.builder()
                        .adminName("System/Admin")
                        .action("EMAIL_SENT")
                        .details("Switched package from " + oldCode + " to " + pkg.getPackageCode() + " and sent via Email")
                        .timestamp(new java.util.Date())
                        .build());
                }
            } else {
                lead = Lead.builder()
                    .leadIdentifier("LID-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
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
                    .mailSentCount(1)
                    .auditLogs(new ArrayList<>(List.of(LeadAuditLog.builder()
                        .adminName("System/Admin")
                        .action("INITIAL_CONTACT")
                        .details("Lead captured from package email")
                        .timestamp(new java.util.Date())
                        .build())))
                    .build();
            }
                
            if (lead != null) leadRepository.save(lead).block();

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
