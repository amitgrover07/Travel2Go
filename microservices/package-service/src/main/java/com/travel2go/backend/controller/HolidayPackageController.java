package com.travel2go.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.HolidayPackage;
import com.travel2go.backend.repository.HolidayPackageRepository;
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
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class HolidayPackageController {

    private final HolidayPackageRepository repository;
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
        System.out.println("Evicting Redis cache for holiday packages...");
        return redisTemplate.opsForValue().delete("packages:active")
                .then(redisTemplate.opsForValue().delete("packages:all"))
                .then(id != null ? redisTemplate.opsForValue().delete("package:" + id) : Mono.empty())
                .then()
                .doOnSuccess(v -> System.out.println("Evicted cache keys successfully"))
                .doOnError(e -> System.err.println("Cache eviction failed: " + e.getMessage()));
    }

    @GetMapping
    public Flux<HolidayPackage> getActivePackages() {
        String cacheKey = "packages:active";
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMapMany(json -> {
                    try {
                        HolidayPackage[] pkgs = objectMapper.readValue(json, HolidayPackage[].class);
                        System.out.println("Cache HIT: active packages retrieved from Redis");
                        return Flux.fromArray(pkgs);
                    } catch (Exception e) {
                        System.err.println("Failed to parse cached active packages: " + e.getMessage());
                        return Flux.empty();
                    }
                })
                .switchIfEmpty(
                        repository.findByStatus("ACTIVE")
                                .collectList()
                                .flatMap(list -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(list);
                                        System.out.println("Cache MISS: active packages loaded from DB and cached");
                                        return redisTemplate.opsForValue().set(cacheKey, json, Duration.ofHours(1))
                                                .thenReturn(list);
                                    } catch (Exception e) {
                                        System.err.println("Failed to cache active packages: " + e.getMessage());
                                        return Mono.just(list);
                                    }
                                })
                                .flatMapMany(Flux::fromIterable)
                );
    }

    @GetMapping("/all")
    public Flux<HolidayPackage> getAllPackages() {
        String cacheKey = "packages:all";
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMapMany(json -> {
                    try {
                        HolidayPackage[] pkgs = objectMapper.readValue(json, HolidayPackage[].class);
                        System.out.println("Cache HIT: all packages retrieved from Redis");
                        return Flux.fromArray(pkgs);
                    } catch (Exception e) {
                        System.err.println("Failed to parse cached all packages: " + e.getMessage());
                        return Flux.empty();
                    }
                })
                .switchIfEmpty(
                        repository.findAll()
                                .collectList()
                                .flatMap(list -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(list);
                                        System.out.println("Cache MISS: all packages loaded from DB and cached");
                                        return redisTemplate.opsForValue().set(cacheKey, json, Duration.ofHours(1))
                                                .thenReturn(list);
                                    } catch (Exception e) {
                                        System.err.println("Failed to cache all packages: " + e.getMessage());
                                        return Mono.just(list);
                                    }
                                })
                                .flatMapMany(Flux::fromIterable)
                );
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<HolidayPackage>> getPackageById(@PathVariable String id) {
        String cacheKey = "package:" + id;
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(json -> {
                    try {
                        HolidayPackage pkg = objectMapper.readValue(json, HolidayPackage.class);
                        System.out.println("Cache HIT: package " + id + " retrieved from Redis");
                        return Mono.just(ResponseEntity.ok(pkg));
                    } catch (Exception e) {
                        System.err.println("Failed to parse cached package: " + e.getMessage());
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(
                        repository.findById(id)
                                .flatMap(pkg -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(pkg);
                                        System.out.println("Cache MISS: package " + id + " loaded from DB and cached");
                                        return redisTemplate.opsForValue().set(cacheKey, json, Duration.ofHours(6))
                                                .thenReturn(ResponseEntity.ok(pkg));
                                    } catch (Exception e) {
                                        System.err.println("Failed to cache package: " + e.getMessage());
                                        return Mono.just(ResponseEntity.ok(pkg));
                                    }
                                })
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Mono<ResponseEntity<?>> createPackage(@RequestBody HolidayPackage holidayPackage, Authentication authentication) {
        String currentUser = getCurrentUser(authentication);
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
                        return Mono.just(ResponseEntity.status(409).body(Map.of("message", "Package code already exists")));
                    }
                    return repository.save(holidayPackage)
                            .flatMap(savedPkg -> evictCache(null).thenReturn(ResponseEntity.ok(savedPkg)));
                });
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> updatePackage(@PathVariable String id, @RequestBody HolidayPackage holidayPackageDetails, Authentication authentication) {
        String currentUser = getCurrentUser(authentication);
        
        return repository.findById(id)
                .flatMap(existingPackage -> {
                    return repository.findByPackageCode(holidayPackageDetails.getPackageCode())
                            .filter(pkg -> !pkg.getId().equals(id))
                            .hasElements()
                            .flatMap(codeExists -> {
                                if (codeExists) {
                                    return Mono.just(ResponseEntity.status(409).body(Map.of("message", "Package code already exists")));
                                }
                                
                                existingPackage.setPackageCode(holidayPackageDetails.getPackageCode());
                                existingPackage.setTitle(holidayPackageDetails.getTitle());
                                existingPackage.setDestination(holidayPackageDetails.getDestination());
                                existingPackage.setStatus(holidayPackageDetails.getStatus());
                                existingPackage.setOverview(holidayPackageDetails.getOverview());
                                existingPackage.setSpecialNotes(holidayPackageDetails.getSpecialNotes());

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
