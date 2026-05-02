package com.travel2go.backend.controller;

import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.HolidayPackageRepository;
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
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class HolidayPackageController {

    private final HolidayPackageRepository repository;

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
    public Flux<HolidayPackage> getActivePackages() {
        return repository.findByStatus("ACTIVE");
    }

    @GetMapping("/all")
    public Flux<HolidayPackage> getAllPackages() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<HolidayPackage> getPackageById(@PathVariable String id) {
        HolidayPackage pkg = repository.findById(id).block();
        if (pkg != null) {
            return ResponseEntity.ok(pkg);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> createPackage(@RequestBody HolidayPackage holidayPackage) {
        String currentUser = getCurrentUser();
        HolidayPackage.Audit audit = HolidayPackage.Audit.builder()
                .createdBy(currentUser)
                .createdAt(Instant.now())
                .updatedBy(currentUser)
                .updatedAt(Instant.now())
                .build();
        holidayPackage.setAudit(audit);

        Boolean exists = repository.findByPackageCode(holidayPackage.getPackageCode()).hasElements().block();
        if (Boolean.TRUE.equals(exists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Package code already exists"));
        }
        HolidayPackage saved = repository.save(holidayPackage).block();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePackage(@PathVariable String id, @RequestBody HolidayPackage holidayPackageDetails) {
        String currentUser = getCurrentUser();
        
        HolidayPackage existingPackage = repository.findById(id).block();
        if (existingPackage == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean codeExists = repository.findByPackageCode(holidayPackageDetails.getPackageCode())
                .filter(pkg -> !pkg.getId().equals(id))
                .hasElements()
                .block();

        if (Boolean.TRUE.equals(codeExists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Package code already exists"));
        }
        
        existingPackage.setPackageCode(holidayPackageDetails.getPackageCode());
        existingPackage.setTitle(holidayPackageDetails.getTitle());
        existingPackage.setDestination(holidayPackageDetails.getDestination());
        existingPackage.setStatus(holidayPackageDetails.getStatus());
        existingPackage.setOverview(holidayPackageDetails.getOverview());

        existingPackage.setDuration(holidayPackageDetails.getDuration());
        existingPackage.setPricing(holidayPackageDetails.getPricing());
        existingPackage.setMedia(holidayPackageDetails.getMedia());

        existingPackage.setInclusions(holidayPackageDetails.getInclusions());
        existingPackage.setExclusions(holidayPackageDetails.getExclusions());
        existingPackage.setItinerary(holidayPackageDetails.getItinerary());

        if (existingPackage.getAudit() == null) {
            existingPackage.setAudit(new HolidayPackage.Audit());
        }
        existingPackage.getAudit().setUpdatedBy(currentUser);
        existingPackage.getAudit().setUpdatedAt(Instant.now());

        HolidayPackage saved = repository.save(existingPackage).block();
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePackage(@PathVariable String id) {
        HolidayPackage existingPackage = repository.findById(id).block();
        if (existingPackage != null) {
            repository.delete(existingPackage).block();
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
