package com.travel2go.backend.listener;

import com.travel2go.backend.controller.NotificationController.NotificationRequest;
import com.travel2go.backend.service.EmailService;
import com.travel2go.backend.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingNotificationListener {

    private final PdfService pdfService;
    private final EmailService emailService;

    @RabbitListener(queues = "booking.confirmation.queue")
    public void receiveBookingNotification(NotificationRequest request) {
        System.out.println("RabbitMQ Listener: Received booking notification event for: " + 
                           (request.bookingRequest != null ? request.bookingRequest.getEmail() : "unknown"));
        try {
            byte[] pdfBytes = null;
            if (request.holidayPackage != null) {
                System.out.println("RabbitMQ Listener: Generating package PDF itinerary asynchronously...");
                pdfBytes = pdfService.generatePackagePdf(
                        request.holidayPackage, 
                        request.globalSettings, 
                        request.booking
                );
            }
            
            System.out.println("RabbitMQ Listener: Sending confirmation email asynchronously...");
            emailService.sendBookingConfirmation(request.bookingRequest, pdfBytes);
            System.out.println("RabbitMQ Listener: Successfully dispatched booking notification!");
        } catch (Exception e) {
            System.err.println("RabbitMQ Listener Failure: Failed to compile/send booking notification: " + e.getMessage());
            e.printStackTrace();
            // Bubble up the exception to let Spring AMQP trigger standard queue retry/backoff policies
            throw new RuntimeException("Error processing notification event", e);
        }
    }
}
