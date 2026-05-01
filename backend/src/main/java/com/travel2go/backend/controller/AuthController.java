package com.travel2go.backend.controller;

import com.travel2go.backend.model.User;
import com.travel2go.backend.model.VerificationCode;
import com.travel2go.backend.repository.UserRepository;
import com.travel2go.backend.repository.VerificationCodeRepository;
import com.travel2go.backend.security.JwtUtil;
import com.travel2go.backend.service.EmailService;
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
import java.util.Set;

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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String role = userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            String token = jwtUtil.generateToken(userDetails.getUsername(), role);

            return ResponseEntity.ok(new AuthResponse(token));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider("LOCAL")
                .roles(Set.of("USER")) // Or "ADMIN" depending on business logic
                .enabled(true)
                .build();

        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty() || !"LOCAL".equals(userOptional.get().getProvider())) {
            return ResponseEntity.badRequest().body("User not found or is using social login");
        }

        // Generate 6-digit code
        String code = String.format("%06d", new Random().nextInt(999999));
        
        // Save or update code
        verificationCodeRepository.deleteByEmail(request.getEmail()); // Delete old codes
        VerificationCode verificationCode = VerificationCode.builder()
                .email(request.getEmail())
                .code(code)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .build();
        verificationCodeRepository.save(verificationCode);

        // Send email
        emailService.sendVerificationCode(request.getEmail(), code);

        return ResponseEntity.ok("Verification code sent to email");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        Optional<VerificationCode> codeOptional = verificationCodeRepository.findByEmailAndCode(request.getEmail(), request.getCode());
        
        if (codeOptional.isEmpty() || codeOptional.get().isExpired()) {
            return ResponseEntity.badRequest().body("Invalid or expired verification code");
        }

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        verificationCodeRepository.deleteByEmail(request.getEmail());

        return ResponseEntity.ok("Password reset successfully");
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
}
