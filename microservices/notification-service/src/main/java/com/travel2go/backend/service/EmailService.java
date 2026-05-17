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
}
