package com.travel2go.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.CustomPackage;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.CustomPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/custom-packages")
@RequiredArgsConstructor
public class CustomPackageController {

    private final CustomPackageRepository repository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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

    private Mono<Void> evictCache(String id) {
        System.out.println("Evicting Redis cache for custom packages...");
        return redisTemplate.opsForValue().delete("custom-packages:active")
                .then(redisTemplate.opsForValue().delete("custom-packages:all"))
                .then(id != null ? redisTemplate.opsForValue().delete("custom-package:" + id) : Mono.empty())
                .then()
                .doOnSuccess(v -> System.out.println("Evicted custom cache keys successfully"))
                .onErrorResume(e -> {
                    System.err.println("Custom cache eviction failed (Redis offline?), bypassing: " + e.getMessage());
                    return Mono.empty(); // Fail-safe
                });
    }

    @GetMapping
    public Flux<CustomPackage> getActivePackages() {
        String cacheKey = "custom-packages:active";
        return redisTemplate.opsForValue().get(cacheKey)
                .onErrorResume(e -> {
                    System.err.println("Redis connection failed on getActivePackages, bypassing cache: " + e.getMessage());
                    return Mono.empty(); // Bypasses the cache and falls back to database
                })
                .flatMapMany(json -> {
                    try {
                        CustomPackage[] pkgs = objectMapper.readValue(json, CustomPackage[].class);
                        System.out.println("Cache HIT: active custom packages retrieved from Redis");
                        return Flux.fromArray(pkgs);
                    } catch (Exception e) {
                        System.err.println("Failed to parse cached active custom packages: " + e.getMessage());
                        return Flux.empty();
                    }
                })
                .switchIfEmpty(
                        repository.findByStatus("ACTIVE")
                                .collectList()
                                .flatMap(list -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(list);
                                        System.out.println("Cache MISS: active custom packages loaded from DB and cached");
                                        return redisTemplate.opsForValue().set(cacheKey, json, Duration.ofHours(1))
                                                .onErrorResume(err -> {
                                                    System.err.println("Failed to write to Redis (getActivePackages): " + err.getMessage());
                                                    return Mono.just(true); // Ignore write error and proceed
                                                })
                                                .thenReturn(list);
                                    } catch (Exception e) {
                                        System.err.println("Failed to cache active custom packages: " + e.getMessage());
                                        return Mono.just(list);
                                    }
                                })
                                .flatMapMany(Flux::fromIterable)
                );
    }

    @GetMapping("/all")
    public Flux<CustomPackage> getAllPackages() {
        String cacheKey = "custom-packages:all";
        System.out.println("Fetching custom packages with Redis support...");
        return redisTemplate.opsForValue().get(cacheKey)
                .onErrorResume(e -> {
                    System.err.println("Redis connection failed on getAllPackages, bypassing cache: " + e.getMessage());
                    return Mono.empty(); // Fail-safe
                })
                .flatMapMany(json -> {
                    try {
                        CustomPackage[] pkgs = objectMapper.readValue(json, CustomPackage[].class);
                        System.out.println("Cache HIT: all custom packages retrieved from Redis");
                        return Flux.fromArray(pkgs);
                    } catch (Exception e) {
                        System.err.println("Failed to parse cached all custom packages: " + e.getMessage());
                        return Flux.empty();
                    }
                })
                .switchIfEmpty(
                        repository.findAll()
                                .collectList()
                                .flatMap(list -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(list);
                                        System.out.println("Cache MISS: all custom packages loaded from DB and cached");
                                        return redisTemplate.opsForValue().set(cacheKey, json, Duration.ofHours(1))
                                                .onErrorResume(err -> {
                                                    System.err.println("Failed to write to Redis (getAllPackages): " + err.getMessage());
                                                    return Mono.just(true); // Ignore write error and proceed
                                                })
                                                .thenReturn(list);
                                    } catch (Exception e) {
                                        System.err.println("Failed to cache all custom packages: " + e.getMessage());
                                        return Mono.just(list);
                                    }
                                })
                                .flatMapMany(Flux::fromIterable)
                );
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<CustomPackage>> getPackageById(@PathVariable String id) {
        String cacheKey = "custom-package:" + id;
        return redisTemplate.opsForValue().get(cacheKey)
                .onErrorResume(e -> {
                    System.err.println("Redis connection failed on getPackageById, bypassing cache: " + e.getMessage());
                    return Mono.empty(); // Fail-safe
                })
                .flatMap(json -> {
                    try {
                        CustomPackage pkg = objectMapper.readValue(json, CustomPackage.class);
                        System.out.println("Cache HIT: custom package " + id + " retrieved from Redis");
                        return Mono.just(ResponseEntity.ok(pkg));
                    } catch (Exception e) {
                        System.err.println("Failed to parse cached custom package: " + e.getMessage());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(
                        repository.findById(id)
                                .flatMap(pkg -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(pkg);
                                        System.out.println("Cache MISS: custom package " + id + " loaded from DB and cached");
                                        return redisTemplate.opsForValue().set(cacheKey, json, Duration.ofHours(6))
                                                .onErrorResume(err -> {
                                                    System.err.println("Failed to write to Redis (getPackageById): " + err.getMessage());
                                                    return Mono.just(true); // Ignore write error and proceed
                                                })
                                                .thenReturn(ResponseEntity.ok(pkg));
                                    } catch (Exception e) {
                                        System.err.println("Failed to cache custom package: " + e.getMessage());
                                        return Mono.just(ResponseEntity.ok(pkg));
                                    }
                                })
                )
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
                            .flatMap(savedPkg -> evictCache(null).thenReturn(ResponseEntity.ok(savedPkg)));
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
                                        .flatMap(savedPkg -> evictCache(savedPkg.getId()).thenReturn(ResponseEntity.ok(savedPkg)));
                            });
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deletePackage(@PathVariable String id) {
        return repository.findById(id)
                .flatMap(existingPackage -> 
                    repository.delete(existingPackage)
                            .then(evictCache(id))
                            .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
