package com.travel2go.backend.controller;

import com.travel2go.backend.model.TravelConfiguration;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.TravelConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/configurators")
@RequiredArgsConstructor
public class TravelConfigurationController {

    private final TravelConfigurationRepository repository;

    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            return (String) principal;
        }
        return principal.toString();
    }

    @GetMapping
    public Flux<TravelConfiguration> getActiveConfigurations() {
        return repository.findByStatus("ACTIVE");
    }

    @GetMapping("/all")
    public Flux<TravelConfiguration> getAllConfigurations() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TravelConfiguration> getConfigurationById(@PathVariable String id) {
        TravelConfiguration config = repository.findById(id).block();
        if (config != null) {
            return ResponseEntity.ok(config);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> createConfiguration(@RequestBody TravelConfiguration config) {
        String currentUser = getCurrentUser();
        HolidayPackage.Audit audit = HolidayPackage.Audit.builder()
                .createdBy(currentUser)
                .createdAt(Instant.now())
                .updatedBy(currentUser)
                .updatedAt(Instant.now())
                .build();
        config.setAudit(audit);

        Boolean exists = repository.findByConfigCode(config.getConfigCode()).hasElements().block();
        if (Boolean.TRUE.equals(exists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Configuration code already exists"));
        }
        TravelConfiguration saved = repository.save(config).block();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateConfiguration(@PathVariable String id, @RequestBody TravelConfiguration configDetails) {
        String currentUser = getCurrentUser();
        
        TravelConfiguration existingConfig = repository.findById(id).block();
        if (existingConfig == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean codeExists = repository.findByConfigCode(configDetails.getConfigCode())
                .filter(cfg -> !cfg.getId().equals(id))
                .hasElements()
                .block();

        if (Boolean.TRUE.equals(codeExists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Configuration code already exists"));
        }
        
        existingConfig.setConfigCode(configDetails.getConfigCode());
        existingConfig.setTitle(configDetails.getTitle());
        existingConfig.setDescription(configDetails.getDescription());
        existingConfig.setStatus(configDetails.getStatus());
        existingConfig.setTargetType(configDetails.getTargetType());
        existingConfig.setTargetValue(configDetails.getTargetValue());
        existingConfig.setOptions(configDetails.getOptions());

        if (existingConfig.getAudit() == null) {
            existingConfig.setAudit(new HolidayPackage.Audit());
        }
        existingConfig.getAudit().setUpdatedBy(currentUser);
        existingConfig.getAudit().setUpdatedAt(Instant.now());

        TravelConfiguration saved = repository.save(existingConfig).block();
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/attached")
    public Flux<TravelConfiguration> getAttachedConfigurations(
            @RequestParam(required = false) String packageId,
            @RequestParam(required = false) String destination) {
        return repository.findByStatus("ACTIVE")
                .filter(cfg -> {
                    if ("PACKAGE".equalsIgnoreCase(cfg.getTargetType()) && packageId != null) {
                        return packageId.equals(cfg.getTargetValue());
                    } else if ("DESTINATION".equalsIgnoreCase(cfg.getTargetType()) && destination != null) {
                        return destination.equalsIgnoreCase(cfg.getTargetValue());
                    }
                    return false;
                });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfiguration(@PathVariable String id) {
        TravelConfiguration existingConfig = repository.findById(id).block();
        if (existingConfig != null) {
            repository.delete(existingConfig).block();
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
