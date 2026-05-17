package com.travel2go.backend.controller;

import com.travel2go.backend.model.Lead;
import com.travel2go.backend.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/bookings/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadRepository leadRepository;

    @GetMapping
    public Flux<Lead> getAllLeads() {
        return leadRepository.findAll();
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Lead>> updateLead(@PathVariable String id, @RequestBody Lead updatedLead) {
        return leadRepository.findById(id)
            .flatMap(existingLead -> {
                existingLead.setStatus(updatedLead.getStatus() != null ? updatedLead.getStatus() : existingLead.getStatus());
                existingLead.setNotes(updatedLead.getNotes() != null ? updatedLead.getNotes() : existingLead.getNotes());
                if (updatedLead.getBestTimeToReach() != null) {
                    existingLead.setBestTimeToReach(updatedLead.getBestTimeToReach());
                }
                return leadRepository.save(existingLead);
            })
            .map(savedLead -> ResponseEntity.ok(savedLead))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
