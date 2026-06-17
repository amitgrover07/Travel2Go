package com.travel2go.backend.controller;

import com.travel2go.backend.model.CustomPackage;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.CustomPackageRepository;
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
@RequestMapping("/api/custom-packages")
@RequiredArgsConstructor
public class CustomPackageController {

    private final CustomPackageRepository repository;

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
    public Flux<CustomPackage> getActivePackages() {
        return repository.findByStatus("ACTIVE");
    }

    @GetMapping("/all")
    public Flux<CustomPackage> getAllPackages() {
        System.out.println("Fetching all custom packages from repository...");
        return repository.findAll()
                .doOnComplete(() -> System.out.println("Completed fetching custom packages"))
                .doOnError(e -> System.err.println("Error fetching custom packages: " + e.getMessage()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomPackage> getPackageById(@PathVariable String id) {
        CustomPackage pkg = repository.findById(id).block();
        if (pkg != null) {
            return ResponseEntity.ok(pkg);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> createPackage(@RequestBody CustomPackage customPackage) {
        String currentUser = getCurrentUser();
        HolidayPackage.Audit audit = HolidayPackage.Audit.builder()
                .createdBy(currentUser)
                .createdAt(Instant.now())
                .updatedBy(currentUser)
                .updatedAt(Instant.now())
                .build();
        customPackage.setAudit(audit);

        if (customPackage.getPricing() != null) {
            double base = customPackage.getPricing().getBasePrice();
            double disc = customPackage.getPricing().getDiscountPercentage();
            customPackage.getPricing().setFinalPrice(Math.ceil(base - (base * (disc / 100.0))));
        }

        Boolean exists = repository.findByPackageCode(customPackage.getPackageCode()).hasElements().block();
        if (Boolean.TRUE.equals(exists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Package code already exists"));
        }
        CustomPackage saved = repository.save(customPackage).block();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePackage(@PathVariable String id, @RequestBody CustomPackage customPackageDetails) {
        String currentUser = getCurrentUser();
        
        CustomPackage existingPackage = repository.findById(id).block();
        if (existingPackage == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean codeExists = repository.findByPackageCode(customPackageDetails.getPackageCode())
                .filter(pkg -> !pkg.getId().equals(id))
                .hasElements()
                .block();

        if (Boolean.TRUE.equals(codeExists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Package code already exists"));
        }
        
        existingPackage.setPackageCode(customPackageDetails.getPackageCode());
        existingPackage.setTitle(customPackageDetails.getTitle());
        existingPackage.setDestination(customPackageDetails.getDestination());
        existingPackage.setStatus(customPackageDetails.getStatus());
        existingPackage.setPackageType(customPackageDetails.getPackageType() != null ? customPackageDetails.getPackageType() : "Custom");
        existingPackage.setOverview(customPackageDetails.getOverview());
        existingPackage.setSpecialNotes(customPackageDetails.getSpecialNotes());

        existingPackage.setDuration(customPackageDetails.getDuration());
        if (customPackageDetails.getPricing() != null) {
            double base = customPackageDetails.getPricing().getBasePrice();
            double disc = customPackageDetails.getPricing().getDiscountPercentage();
            customPackageDetails.getPricing().setFinalPrice(Math.ceil(base - (base * (disc / 100.0))));
        }
        existingPackage.setPricing(customPackageDetails.getPricing());
        existingPackage.setMedia(customPackageDetails.getMedia());

        existingPackage.setInclusions(customPackageDetails.getInclusions());
        existingPackage.setExclusions(customPackageDetails.getExclusions());
        existingPackage.setItinerary(customPackageDetails.getItinerary());

        if (existingPackage.getAudit() == null) {
            existingPackage.setAudit(new HolidayPackage.Audit());
        }
        existingPackage.getAudit().setUpdatedBy(currentUser);
        existingPackage.getAudit().setUpdatedAt(Instant.now());

        CustomPackage saved = repository.save(existingPackage).block();
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePackage(@PathVariable String id) {
        CustomPackage existingPackage = repository.findById(id).block();
        if (existingPackage != null) {
            repository.delete(existingPackage).block();
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
