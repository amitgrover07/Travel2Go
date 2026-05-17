package com.travel2go.backend.controller;

import com.travel2go.backend.model.Lead;
import com.travel2go.backend.repository.LeadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.travel2go.backend.model.LeadAuditLog;
import com.travel2go.backend.model.LeadActivity;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

@RestController
@RequestMapping("/api/bookings/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadRepository leadRepository;

    @GetMapping
    public List<Lead> getAllLeads() {
        return leadRepository.findAll().collectList().block();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Lead> updateLead(@PathVariable String id, @RequestBody Lead updatedLead) {
        Lead existingLead = leadRepository.findById(id).block();
        if (existingLead == null) {
            return ResponseEntity.notFound().build();
        }
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminName = (auth != null && auth.getName() != null) ? auth.getName() : "Admin";
        
        if (existingLead.getAuditLogs() == null) {
            existingLead.setAuditLogs(new ArrayList<>());
        }

        // Check for Status Change
        if (updatedLead.getStatus() != null && !updatedLead.getStatus().equals(existingLead.getStatus())) {
            existingLead.getAuditLogs().add(LeadAuditLog.builder()
                .adminName(adminName)
                .action("STATUS_CHANGE")
                .details("Moved from " + existingLead.getStatus() + " to " + updatedLead.getStatus())
                .timestamp(new Date())
                .build());
            existingLead.setStatus(updatedLead.getStatus());
        }

        // Check for Customer Info Changes (detailed audit trail)
        List<String> changesList = new ArrayList<>();
        if (updatedLead.getFirstName() != null && !updatedLead.getFirstName().equals(existingLead.getFirstName())) {
            changesList.add("First Name");
            existingLead.setFirstName(updatedLead.getFirstName());
        }
        if (updatedLead.getLastName() != null && !updatedLead.getLastName().equals(existingLead.getLastName())) {
            changesList.add("Last Name");
            existingLead.setLastName(updatedLead.getLastName());
        }
        if (updatedLead.getEmail() != null && !updatedLead.getEmail().equals(existingLead.getEmail())) {
            changesList.add("Email");
            existingLead.setEmail(updatedLead.getEmail());
        }
        if (updatedLead.getPhone() != null && !updatedLead.getPhone().equals(existingLead.getPhone())) {
            changesList.add("Phone");
            existingLead.setPhone(updatedLead.getPhone());
        }
        if (updatedLead.getLocation() != null && !updatedLead.getLocation().equals(existingLead.getLocation())) {
            changesList.add("Location");
            existingLead.setLocation(updatedLead.getLocation());
        }

        if (!changesList.isEmpty()) {
            existingLead.getAuditLogs().add(LeadAuditLog.builder()
                .adminName(adminName)
                .action("DETAILS_UPDATED")
                .details("Edited customer information: " + String.join(", ", changesList))
                .timestamp(new Date())
                .build());
        }

        // Handle Call Time (separate from editing user info)
        if (updatedLead.getBestTimeToReach() != null && !updatedLead.getBestTimeToReach().equals(existingLead.getBestTimeToReach())) {
            String oldCallTime = existingLead.getBestTimeToReach() != null ? existingLead.getBestTimeToReach() : "Not specified";
            existingLead.setBestTimeToReach(updatedLead.getBestTimeToReach());
            existingLead.getAuditLogs().add(LeadAuditLog.builder()
                .adminName(adminName)
                .action("CALL_TIME_UPDATED")
                .details("Updated call time from \"" + oldCallTime + "\" to \"" + updatedLead.getBestTimeToReach() + "\"")
                .timestamp(new Date())
                .build());
        }

        if (updatedLead.getNotes() != null && !updatedLead.getNotes().equals(existingLead.getNotes())) {
            existingLead.setNotes(updatedLead.getNotes());
        }
        
        Lead savedLead = leadRepository.save(existingLead).block();
        return ResponseEntity.ok(savedLead);
    }

    @PostMapping("/{id}/activities")
    public ResponseEntity<Lead> addActivity(@PathVariable String id, @RequestBody LeadActivity activity) {
        Lead existingLead = leadRepository.findById(id).block();
        if (existingLead == null) {
            return ResponseEntity.notFound().build();
        }
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminName = (auth != null && auth.getName() != null) ? auth.getName() : "Admin";
        
        if (existingLead.getActivities() == null) {
            existingLead.setActivities(new ArrayList<>());
        }
        
        // Generate system generated unique activity ID (e.g., ACT-XXXXXX)
        String activityId = "ACT-" + String.format("%06d", new Random().nextInt(1000000));
        
        activity.setActivityId(activityId);
        activity.setAdminName(adminName);
        activity.setTimestamp(new Date());
        
        existingLead.getActivities().add(activity);
        
        // Add to Audit Logs
        if (existingLead.getAuditLogs() == null) {
            existingLead.setAuditLogs(new ArrayList<>());
        }
        existingLead.getAuditLogs().add(LeadAuditLog.builder()
            .adminName(adminName)
            .action("ACTIVITY_CREATED")
            .details("Created activity " + activityId + " (" + activity.getType() + ")")
            .timestamp(new Date())
            .build());
            
        Lead saved = leadRepository.save(existingLead).block();
        return ResponseEntity.ok(saved);
    }
}
