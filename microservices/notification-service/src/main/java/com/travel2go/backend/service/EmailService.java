package com.travel2go.backend.service;

import com.travel2go.backend.dto.BookingRequest;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

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
        
        if (pdfBytes != null) {
            String fileName = request.getPackageTitle().replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
            helper.addAttachment(fileName, new ByteArrayResource(pdfBytes));
        }
        
        mailSender.send(message);
    }
}
