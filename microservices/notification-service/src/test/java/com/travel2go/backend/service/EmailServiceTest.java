package com.travel2go.backend.service;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private Storage storage;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, storage);
    }

    @Test
    void sendLegBookingConfirmation_sendsPlainTextEmailToRecipient() {
        emailService.sendLegBookingConfirmation("traveller@example.com", "leg-1", 150000L, "booking-1");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("traveller@example.com");
        assertThat(captor.getValue().getSubject()).contains("confirmed");
        assertThat(captor.getValue().getText()).contains("booking-1");
    }

    @Test
    void sendLegBookingConfirmation_skipsSendWhenEmailIsBlank() {
        emailService.sendLegBookingConfirmation(null, "leg-1", 150000L, "booking-1");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
