package com.travel2go.backend.controller;

import com.travel2go.backend.model.User;
import com.travel2go.backend.model.VerificationCode;
import com.travel2go.backend.repository.UserRepository;
import com.travel2go.backend.repository.VerificationCodeRepository;
import com.travel2go.backend.security.JwtUtil;
import com.travel2go.backend.service.EmailService;
import com.travel2go.backend.service.SmsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String role = userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            
            User user = userRepository.findByEmail(userDetails.getUsername()).block();
            String name = user != null && user.getName() != null ? user.getName() : "";
            String picture = user != null && user.getPicture() != null ? user.getPicture() : "";
            
            String token = jwtUtil.generateToken(userDetails.getUsername(), role, name, picture);

            return ResponseEntity.ok(new AuthResponse(token));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.getEmail() == null || !request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters");
        }

        String cleanEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(cleanEmail).block() != null) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        User user = User.builder()
                .email(cleanEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .provider("LOCAL")
                .roles(List.of("USER"))
                .enabled(true)
                .build();

        userRepository.save(user).block();
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        if (request.getEmail() == null || !request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }

        String cleanEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(cleanEmail).block();
        if (user == null || !"LOCAL".equals(user.getProvider())) {
            return ResponseEntity.badRequest().body("User not found or is using social login");
        }

        // Generate 6-digit code
        String code = String.format("%06d", new Random().nextInt(999999));
        
        // Save or update code
        verificationCodeRepository.deleteByEmail(cleanEmail).block(); // Delete old codes
        VerificationCode verificationCode = VerificationCode.builder()
                .email(cleanEmail)
                .code(code)
                .expiryDate(new java.util.Date(System.currentTimeMillis() + 15 * 60 * 1000))
                .build();
        verificationCodeRepository.save(verificationCode).block();

        // Send email
        emailService.sendVerificationCode(cleanEmail, code);

        return ResponseEntity.ok("Verification code sent to email");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        if (request.getEmail() == null || !request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Verification code is required");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            return ResponseEntity.badRequest().body("New password must be at least 8 characters");
        }

        String cleanEmail = request.getEmail().trim().toLowerCase();
        String cleanCode = request.getCode().trim();

        VerificationCode code = verificationCodeRepository.findByEmailAndCode(cleanEmail, cleanCode).block();
        
        if (code == null || code.isExpired()) {
            return ResponseEntity.badRequest().body("Invalid or expired verification code");
        }

        User user = userRepository.findByEmail(cleanEmail).block();
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user).block();
        
        verificationCodeRepository.deleteByEmail(cleanEmail).block();

        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/send-login-otp")
    public ResponseEntity<?> sendLoginOtp(@RequestBody PhoneAuthRequest request) {
        if (request.getPhone() == null || request.getPhone().trim().length() < 10) {
            return ResponseEntity.badRequest().body("Valid phone number is required (min 10 characters)");
        }
        
        String cleanPhone = request.getPhone().replaceAll("[^0-9+]", "");
        String code = String.format("%06d", new Random().nextInt(999999));
        
        verificationCodeRepository.deleteByPhone(cleanPhone).block();
        VerificationCode verificationCode = VerificationCode.builder()
                .phone(cleanPhone)
                .code(code)
                .expiryDate(new java.util.Date(System.currentTimeMillis() + 5 * 60 * 1000))
                .build();
        verificationCodeRepository.save(verificationCode).block();

        smsService.sendVerificationCode(cleanPhone, code);
        return ResponseEntity.ok("OTP sent to phone");
    }

    @PostMapping("/verify-login-otp")
    public ResponseEntity<?> verifyLoginOtp(@RequestBody VerifyOtpRequest request) {
        if (request.getPhone() == null || request.getPhone().trim().length() < 10) {
            return ResponseEntity.badRequest().body("Valid phone number is required");
        }
        if (request.getOtp() == null || request.getOtp().trim().length() != 6) {
            return ResponseEntity.badRequest().body("OTP must be exactly 6 digits");
        }

        String cleanPhone = request.getPhone().replaceAll("[^0-9+]", "");
        String cleanOtp = request.getOtp().trim();

        VerificationCode code = verificationCodeRepository.findByPhoneAndCode(cleanPhone, cleanOtp).block();
        if (code == null || code.isExpired()) {
            return ResponseEntity.status(401).body("Invalid or expired OTP");
        }

        User user = userRepository.findByPhone(cleanPhone).block();
        if (user == null) {
            user = User.builder()
                    .phone(cleanPhone)
                    .provider("PHONE")
                    .roles(List.of("USER"))
                    .enabled(true)
                    .build();
            userRepository.save(user).block();
        }

        String role = user.getRoles() != null && !user.getRoles().isEmpty() ? user.getRoles().get(0) : "USER";
        String name = user.getName() != null ? user.getName() : cleanPhone;
        String picture = user.getPicture() != null ? user.getPicture() : "";
        
        String token = jwtUtil.generateToken(cleanPhone, role, name, picture);
        
        verificationCodeRepository.deleteByPhone(cleanPhone).block();

        return ResponseEntity.ok(new AuthResponse(token));
    }

    // DTOs
    @Data
    public static class AuthRequest {
        private String username; // This is actually email now
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        public AuthResponse(String token) { this.token = token; }
    }
    
    @Data
    public static class RegisterRequest {
        private String email;
        private String password;
    }

    @Data
    public static class ForgotPasswordRequest {
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        private String email;
        private String code;
        private String newPassword;
    }

    @Data
    public static class PhoneAuthRequest {
        private String phone;
    }

    @Data
    public static class VerifyOtpRequest {
        private String phone;
        private String otp;
    }
}
