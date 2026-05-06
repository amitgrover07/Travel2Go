package com.travel2go.backend.controller;

import com.travel2go.backend.model.GlobalSettings;
import com.travel2go.backend.repository.GlobalSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final GlobalSettingsRepository repository;
    private static final String GLOBAL_ID = "global";

    @GetMapping("/terms")
    public Mono<ResponseEntity<GlobalSettings>> getTerms() {
        return repository.findById(GLOBAL_ID)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(GlobalSettings.builder()
                        .id(GLOBAL_ID)
                        .termsAndConditions("")
                        .build()));
    }

    @PutMapping("/terms")
    public Mono<ResponseEntity<?>> updateTerms(@RequestBody Map<String, String> payload) {
        String terms = payload.get("termsAndConditions");
        return repository.findById(GLOBAL_ID)
                .defaultIfEmpty(GlobalSettings.builder().id(GLOBAL_ID).build())
                .flatMap(settings -> {
                    settings.setTermsAndConditions(terms);
                    return repository.save(settings);
                })
                .map(ResponseEntity::ok);
    }
}
