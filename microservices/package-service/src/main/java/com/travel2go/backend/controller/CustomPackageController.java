package com.travel2go.backend.controller;

import com.travel2go.backend.model.CustomPackage;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.CustomPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/custom-packages")
@RequiredArgsConstructor
public class CustomPackageController {

    private final CustomPackageRepository repository;

    private String getCurrentUser(Authentication authentication) {
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
    public Mono<ResponseEntity<CustomPackage>> getPackageById(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Mono<ResponseEntity<?>> createPackage(@RequestBody CustomPackage customPackage, Authentication authentication) {
        String currentUser = getCurrentUser(authentication);
        HolidayPackage.Audit audit = HolidayPackage.Audit.builder()
                .createdBy(currentUser)
                .createdAt(Instant.now())
                .updatedBy(currentUser)
                .updatedAt(Instant.now())
                .build();
        customPackage.setAudit(audit);

        return repository.findByPackageCode(customPackage.getPackageCode())
                .hasElements()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(ResponseEntity.status(409).body(Map.of("message", "Package code already exists")));
                    }
                    return repository.save(customPackage)
                            .map(ResponseEntity::ok);
                });
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> updatePackage(@PathVariable String id, @RequestBody CustomPackage customPackageDetails, Authentication authentication) {
        String currentUser = getCurrentUser(authentication);
        
        return repository.findById(id)
                .flatMap(existingPackage -> {
                    return repository.findByPackageCode(customPackageDetails.getPackageCode())
                            .filter(pkg -> !pkg.getId().equals(id))
                            .hasElements()
                            .flatMap(codeExists -> {
                                if (codeExists) {
                                    return Mono.just(ResponseEntity.status(409).body(Map.of("message", "Package code already exists")));
                                }
                                
                                existingPackage.setPackageCode(customPackageDetails.getPackageCode());
                                existingPackage.setTitle(customPackageDetails.getTitle());
                                existingPackage.setDestination(customPackageDetails.getDestination());
                                existingPackage.setStatus(customPackageDetails.getStatus());
                                existingPackage.setPackageType(customPackageDetails.getPackageType() != null ? customPackageDetails.getPackageType() : "Custom");
                                existingPackage.setOverview(customPackageDetails.getOverview());
                                existingPackage.setSpecialNotes(customPackageDetails.getSpecialNotes());

                                existingPackage.setDuration(customPackageDetails.getDuration());
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

                                return repository.save(existingPackage)
                                        .map(ResponseEntity::ok);
                            });
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deletePackage(@PathVariable String id) {
        return repository.findById(id)
                .flatMap(existingPackage -> 
                    repository.delete(existingPackage)
                            .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
