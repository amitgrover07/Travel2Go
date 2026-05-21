package com.travel2go.backend.controller;

import com.travel2go.backend.model.User;
import com.travel2go.backend.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findAll()
                .collectList()
                .block();
        if (users != null) {
            users.forEach(u -> u.setPassword(null));
        }
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable String id,
            @RequestBody RoleUpdateRequest request,
            Authentication authentication
    ) {
        User user = userRepository.findById(id).block();
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // Prevent self-lockout: Admin shouldn't remove ADMIN role from themselves
        if (authentication != null && authentication.isAuthenticated()) {
            String currentUsername = authentication.getName(); // this can be email or phone
            if ((currentUsername.equals(user.getEmail()) || currentUsername.equals(user.getPhone()))
                    && !request.getRoles().contains("ADMIN")) {
                return ResponseEntity.badRequest().body("You cannot remove the ADMIN role from yourself.");
            }
        }

        user.setRoles(request.getRoles());
        User saved = userRepository.save(user).block();
        if (saved != null) {
            saved.setPassword(null);
        }
        return ResponseEntity.ok(saved);
    }

    @Data
    public static class RoleUpdateRequest {
        private List<String> roles;
    }
}
