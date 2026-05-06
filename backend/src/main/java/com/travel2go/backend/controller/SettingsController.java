package com.travel2go.backend.controller;

import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.repository.GlobalSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final GlobalSettingsRepository repository;
    private static final String GLOBAL_ID = "global";

    @GetMapping("/terms")
    public ResponseEntity<GlobalSettings> getTerms() {
        GlobalSettings settings = repository.findById(GLOBAL_ID).block();
        if (settings == null) {
            settings = GlobalSettings.builder()
                    .id(GLOBAL_ID)
                    .termsAndConditions("")
                    .build();
        }
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/terms")
    public ResponseEntity<?> updateTerms(@RequestBody Map<String, String> payload) {
        String terms = payload.get("termsAndConditions");
        GlobalSettings settings = repository.findById(GLOBAL_ID).block();
        if (settings == null) {
            settings = GlobalSettings.builder().id(GLOBAL_ID).build();
        }
        settings.setTermsAndConditions(terms);
        GlobalSettings saved = repository.save(settings).block();
        return ResponseEntity.ok(saved);
    }
}
