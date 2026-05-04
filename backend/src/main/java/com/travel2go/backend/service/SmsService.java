package com.travel2go.backend.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SmsService {

    public void sendVerificationCode(String phone, String code) {
        // In a real application, you would integrate Twilio or another SMS gateway here.
        // For now, we just log it to the console.
        log.info("=========================================================");
        log.info("MOCK SMS SENT TO: {}", phone);
        log.info("YOUR OTP IS: {}", code);
        log.info("=========================================================");
    }
}
