package com.travel2go.backend.controller;

import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.HolidayPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class HolidayPackageController {

    private final HolidayPackageRepository repository;

    @GetMapping
    public List<HolidayPackage> getActivePackages() {
        return repository.findByStatus("ACTIVE");
    }

    @GetMapping("/all")
    public List<HolidayPackage> getAllPackages() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<HolidayPackage> getPackageById(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public HolidayPackage createPackage(@RequestBody HolidayPackage holidayPackage) {
        return repository.save(holidayPackage);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HolidayPackage> updatePackage(@PathVariable String id, @RequestBody HolidayPackage holidayPackageDetails) {
        return repository.findById(id)
                .map(existingPackage -> {
                    // Update version so MongoDB can perform optimistic locking check
                    existingPackage.setVersion(holidayPackageDetails.getVersion());

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

                    return ResponseEntity.ok(repository.save(existingPackage));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePackage(@PathVariable String id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Conflict");
        response.put("message", "The document was updated by another user. Please refresh and try again.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
