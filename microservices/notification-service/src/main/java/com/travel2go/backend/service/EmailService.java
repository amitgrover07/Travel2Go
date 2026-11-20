package com.travel2go.backend.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.travel2go.backend.dto.BookingRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final Storage storage;

    @Value("${gcp.email.bucket.name}")
    private String bucketName;

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Password Reset Verification Code");
        message.setText("Your verification code is: " + code + "\n\nThis code will expire in 15 minutes.");
        mailSender.send(message);
    }

    public void sendBookingConfirmation(BookingRequest request, byte[] pdfBytes) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        
        helper.setTo(request.getEmail());
        helper.setSubject("Booking Confirmation: " + request.getPackageTitle());
        
        String content = String.format(
            "Dear %s %s,\n\n" +
            "Thank you for booking with Travel2Go!\n\n" +
            "We have received your interest in the package: %s.\n" +
            "Attached is the full itinerary and details of the package for your reference.\n\n" +
            "Booking Details:\n" +
            "Package: %s\n" +
            "Location: %s\n" +
            "Phone: %s\n\n" +
            "Our team will contact you shortly with more details.\n\n" +
            "Best Regards,\n" +
            "Travel2Go Team",
            request.getFirstName(),
            request.getLastName() != null ? request.getLastName() : "",
            request.getPackageTitle(),
            request.getPackageTitle(),
            request.getLocation(),
            request.getPhone()
        );
        
        helper.setText(content);
        
        String fileName = request.getPackageTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

        if (pdfBytes != null) {
            helper.addAttachment(fileName, new ByteArrayResource(pdfBytes));
        }
        
        mailSender.send(message);

        // Upload the actual sent email (.eml format) to Google Cloud Storage (GCS)
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            message.writeTo(baos);
            byte[] emlBytes = baos.toByteArray();

            String dateFolder = LocalDate.now().toString(); // format: YYYY-MM-DD
            String emailFileName = "Email_" + request.getPackageTitle().replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".eml";
            String gcsBlobName = dateFolder + "/" + request.getEmail() + "/" + emailFileName;
            
            System.out.println("[GCS] Uploading email message (.eml) to GCS bucket: " + bucketName + " path: " + gcsBlobName);
            BlobId blobId = BlobId.of(bucketName, gcsBlobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("message/rfc822")
                    .build();
            
            storage.create(blobInfo, emlBytes);
            System.out.println("[GCS] Successfully uploaded sent email to GCS.");
        } catch (Exception e) {
            System.err.println("[GCS] Error uploading email to Google Cloud Storage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendLegBookingConfirmation(String email, String legType, long amountPaise, String bookingReference) {
        if (email == null || email.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your Travel2Go booking is confirmed");
        message.setText(String.format(
                java.util.Locale.ROOT,
                "Great news - your booking is confirmed!\n\n" +
                "Reference: %s\n" +
                "Type: %s\n" +
                "Amount paid: Rs. %.2f\n\n" +
                "Thank you for booking with Travel2Go.",
                bookingReference, legType, amountPaise / 100.0));
        mailSender.send(message);
    }

    /**
     * Proactive disruption notice (F4). The copy depends on the outcome: already
     * rebooked, refunded, or awaiting consent for a fare-increasing option.
     */
    public void sendDisruptionNotice(String email, String legType, String outcome, String rebookedToLegId) {
        if (email == null || email.isBlank()) {
            return;
        }
        String legLabel = legType == null ? "trip" : legType.toLowerCase(java.util.Locale.ROOT);
        String subject;
        String body;
        switch (outcome == null ? "" : outcome) {
            case "REBOOKED" -> {
                subject = "We've already rebooked your " + legLabel;
                body = "Your " + legLabel + " was disrupted, but we have already rebooked you onto the next "
                        + "available option at no extra cost. Your new booking reference is " + rebookedToLegId
                        + ". There is nothing you need to do.";
            }
            case "REFUNDED" -> {
                subject = "Your disrupted " + legLabel + " has been refunded";
                body = "Your " + legLabel + " was disrupted and no suitable alternative was available, so we have "
                        + "started a full refund. It will settle within 72 hours.";
            }
            default -> {
                subject = "Action needed: your " + legLabel + " was disrupted";
                body = "Your " + legLabel + " was disrupted. The only alternative we found costs more, so we will "
                        + "not charge you without your say-so. Please confirm if you'd like us to rebook you on it.";
            }
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body + "\n\nTravel2Go");
        mailSender.send(message);
    }
}
