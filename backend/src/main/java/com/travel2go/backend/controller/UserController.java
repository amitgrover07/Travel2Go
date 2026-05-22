package com.travel2go.backend.controller;

import com.travel2go.backend.model.User;
import com.travel2go.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

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

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll()
                .map(user -> {
                    user.setPassword(null);
                    return user;
                })
                .collectList()
                .block();
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<User> updateUserRole(@PathVariable String userId, @RequestBody UserRoleUpdateRequest request) {
        User user = userRepository.findById(userId).block();
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        user.setRoles(request.getRoles());
        User saved = userRepository.save(user).block();
        if (saved != null) {
            saved.setPassword(null);
            return ResponseEntity.ok(saved);
        }
        
        return ResponseEntity.internalServerError().build();
    }

    @Data
    public static class UserRoleUpdateRequest {
        private List<String> roles;
    }
}
