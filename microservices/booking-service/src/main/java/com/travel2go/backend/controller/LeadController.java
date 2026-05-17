package com.travel2go.backend.controller;

import com.travel2go.backend.model.Lead;
import com.travel2go.backend.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        
        existingLead.setStatus(updatedLead.getStatus() != null ? updatedLead.getStatus() : existingLead.getStatus());
        existingLead.setNotes(updatedLead.getNotes() != null ? updatedLead.getNotes() : existingLead.getNotes());
        if (updatedLead.getBestTimeToReach() != null) {
            existingLead.setBestTimeToReach(updatedLead.getBestTimeToReach());
        }
        
        Lead savedLead = leadRepository.save(existingLead).block();
        return ResponseEntity.ok(savedLead);
    }
}
