package com.travel2go.backend.controller;

import com.travel2go.backend.model.Lead;
import com.travel2go.backend.repository.LeadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.travel2go.backend.model.LeadAuditLog;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;

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

        boolean detailsChanged = false;

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

        // Update details
        if (updatedLead.getFirstName() != null && !updatedLead.getFirstName().equals(existingLead.getFirstName())) {
            existingLead.setFirstName(updatedLead.getFirstName()); detailsChanged = true;
        }
        if (updatedLead.getLastName() != null && !updatedLead.getLastName().equals(existingLead.getLastName())) {
            existingLead.setLastName(updatedLead.getLastName()); detailsChanged = true;
        }
        if (updatedLead.getEmail() != null && !updatedLead.getEmail().equals(existingLead.getEmail())) {
            existingLead.setEmail(updatedLead.getEmail()); detailsChanged = true;
        }
        if (updatedLead.getPhone() != null && !updatedLead.getPhone().equals(existingLead.getPhone())) {
            existingLead.setPhone(updatedLead.getPhone()); detailsChanged = true;
        }
        if (updatedLead.getLocation() != null && !updatedLead.getLocation().equals(existingLead.getLocation())) {
            existingLead.setLocation(updatedLead.getLocation()); detailsChanged = true;
        }
        if (updatedLead.getNotes() != null && !updatedLead.getNotes().equals(existingLead.getNotes())) {
            existingLead.setNotes(updatedLead.getNotes());
        }
        if (updatedLead.getBestTimeToReach() != null && !updatedLead.getBestTimeToReach().equals(existingLead.getBestTimeToReach())) {
            existingLead.setBestTimeToReach(updatedLead.getBestTimeToReach()); detailsChanged = true;
        }

        if (detailsChanged) {
            existingLead.getAuditLogs().add(LeadAuditLog.builder()
                .adminName(adminName)
                .action("DETAILS_UPDATED")
                .details("Updated customer details")
                .timestamp(new Date())
                .build());
        }
        
        Lead savedLead = leadRepository.save(existingLead).block();
        return ResponseEntity.ok(savedLead);
    }
}
