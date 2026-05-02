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
import reactor.core.publisher.Mono;

import java.time.Instant;

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
    public Mono<ResponseEntity<HolidayPackage>> getPackageById(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Mono<ResponseEntity<?>> createPackage(@RequestBody HolidayPackage holidayPackage) {
        String currentUser = getCurrentUser();
        HolidayPackage.Audit audit = HolidayPackage.Audit.builder()
                .createdBy(currentUser)
                .createdAt(Instant.now())
                .updatedBy(currentUser)
                .updatedAt(Instant.now())
                .build();
        holidayPackage.setAudit(audit);

        return repository.findByPackageCode(holidayPackage.getPackageCode())
                .hasElements()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(ResponseEntity.status(409).body(java.util.Map.of("message", "Package code already exists")));
                    }
                    return repository.save(holidayPackage).map(ResponseEntity::ok);
                });
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> updatePackage(@PathVariable String id, @RequestBody HolidayPackage holidayPackageDetails) {
        String currentUser = getCurrentUser();
        
        return repository.findById(id)
                .flatMap(existingPackage -> {
                    return repository.findByPackageCode(holidayPackageDetails.getPackageCode())
                            .filter(pkg -> !pkg.getId().equals(id))
                            .hasElements()
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.just(ResponseEntity.status(409).body(java.util.Map.of("message", "Package code already exists")));
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

                                return repository.save(existingPackage).map(ResponseEntity::ok);
                            });
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deletePackage(@PathVariable String id) {
        return repository.findById(id)
                .flatMap(existingPackage -> repository.delete(existingPackage).then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
