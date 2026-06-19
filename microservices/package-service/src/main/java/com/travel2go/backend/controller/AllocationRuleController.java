package com.travel2go.backend.controller;

import com.travel2go.backend.model.AllocationRule;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.AllocationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/allocation-rules")
@RequiredArgsConstructor
public class AllocationRuleController {

    private final AllocationRuleRepository repository;

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
    public Flux<AllocationRule> getActiveRules() {
        return repository.findByStatus("ACTIVE");
    }

    @GetMapping("/all")
    public Flux<AllocationRule> getAllRules() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AllocationRule> getRuleById(@PathVariable String id) {
        AllocationRule rule = repository.findById(id).block();
        if (rule != null) {
            return ResponseEntity.ok(rule);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/package/{packageId}")
    public ResponseEntity<AllocationRule> getRuleByPackageId(@PathVariable String packageId) {
        AllocationRule rule = repository.findByStatus("ACTIVE")
                .filter(r -> r.getMappedPackageIds() != null && r.getMappedPackageIds().contains(packageId))
                .next()
                .block();
        if (rule != null) {
            return ResponseEntity.ok(rule);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody AllocationRule rule) {
        String currentUser = getCurrentUser();
        HolidayPackage.Audit audit = HolidayPackage.Audit.builder()
                .createdBy(currentUser)
                .createdAt(Instant.now())
                .updatedBy(currentUser)
                .updatedAt(Instant.now())
                .build();
        rule.setAudit(audit);

        Boolean exists = repository.findByRuleCode(rule.getRuleCode()).hasElements().block();
        if (Boolean.TRUE.equals(exists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Rule code already exists"));
        }
        AllocationRule saved = repository.save(rule).block();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable String id, @RequestBody AllocationRule ruleDetails) {
        String currentUser = getCurrentUser();
        
        AllocationRule existingRule = repository.findById(id).block();
        if (existingRule == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean codeExists = repository.findByRuleCode(ruleDetails.getRuleCode())
                .filter(r -> !r.getId().equals(id))
                .hasElements()
                .block();

        if (Boolean.TRUE.equals(codeExists)) {
            return ResponseEntity.status(409).body(Map.of("message", "Rule code already exists"));
        }
        
        existingRule.setRuleCode(ruleDetails.getRuleCode());
        existingRule.setTitle(ruleDetails.getTitle());
        existingRule.setDescription(ruleDetails.getDescription());
        existingRule.setStatus(ruleDetails.getStatus());
        existingRule.setBaseRoomRate(ruleDetails.getBaseRoomRate());
        existingRule.setExtraAdultRate(ruleDetails.getExtraAdultRate());
        existingRule.setCwbRate(ruleDetails.getCwbRate());
        existingRule.setCnbRate(ruleDetails.getCnbRate());
        existingRule.setSightseeingTicketPrice(ruleDetails.getSightseeingTicketPrice());
        existingRule.setVehicles(ruleDetails.getVehicles());
        existingRule.setMappedPackageIds(ruleDetails.getMappedPackageIds());
        existingRule.setPackageType(ruleDetails.getPackageType());
        existingRule.setMealPlan(ruleDetails.getMealPlan());
        existingRule.setIncludeSightseeing(ruleDetails.isIncludeSightseeing());

        if (existingRule.getAudit() == null) {
            existingRule.setAudit(new HolidayPackage.Audit());
        }
        existingRule.getAudit().setUpdatedBy(currentUser);
        existingRule.getAudit().setUpdatedAt(Instant.now());

        AllocationRule saved = repository.save(existingRule).block();
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        AllocationRule existingRule = repository.findById(id).block();
        if (existingRule != null) {
            repository.delete(existingRule).block();
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
