package com.travel2go.backend.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account_sid}")
    private String accountSid;

    @Value("${twilio.auth_token}")
    private String authToken;

    @Value("${twilio.phone_number}")
    private String fromPhoneNumber;

    @PostConstruct
    public void init() {
        if (!"placeholder".equals(accountSid) && accountSid != null && !accountSid.isEmpty()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio initialized successfully.");
        } else {
            log.warn("Twilio credentials not set. SMS will be logged to console only.");
        }
    }

    public void sendVerificationCode(String phone, String code) {
        if ("placeholder".equals(accountSid) || accountSid == null || accountSid.isEmpty()) {
            // Fallback to mock behavior if credentials aren't set
            log.info("=========================================================");
            log.info("MOCK SMS SENT TO: {}", phone);
            log.info("YOUR OTP IS: {}", code);
            log.info("=========================================================");
            return;
        }

        try {
            Message message = Message.creator(
                    new PhoneNumber(phone),
                    new PhoneNumber(fromPhoneNumber),
                    "Your Travel2Go verification code is: " + code
            ).create();
            log.info("SMS sent successfully. SID: {}", message.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phone, e.getMessage());
        }
    }
}
