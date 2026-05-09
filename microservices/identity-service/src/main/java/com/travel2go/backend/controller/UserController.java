package com.travel2go.backend.controller;

import com.travel2go.backend.model.User;
import com.travel2go.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        
        String identifier = authentication.getName(); // This is email or phone based on token subject
        
        // Try finding by email first
        User user = userRepository.findByEmail(identifier).block();
        if (user == null) {
            // Fallback to phone
            user = userRepository.findByPhone(identifier).block();
        }
        
        if (user != null) {
            // Don't send password back to the client
            user.setPassword(null);
            return ResponseEntity.ok(user);
        }
        
        return ResponseEntity.notFound().build();
    }
}
