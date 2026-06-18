package com.travel2go.backend.controller;

import com.travel2go.backend.model.ConfiguratorCategory;
import com.travel2go.backend.repository.ConfiguratorCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/configurator-categories")
@RequiredArgsConstructor
public class ConfiguratorCategoryController {

    private final ConfiguratorCategoryRepository repository;

    @GetMapping
    public Flux<ConfiguratorCategory> getAllCategories() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConfiguratorCategory> getCategoryById(@PathVariable String id) {
        ConfiguratorCategory category = repository.findById(id).block();
        if (category != null) {
            return ResponseEntity.ok(category);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<ConfiguratorCategory> createCategory(@RequestBody ConfiguratorCategory category) {
        ConfiguratorCategory saved = repository.save(category).block();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConfiguratorCategory> updateCategory(@PathVariable String id, @RequestBody ConfiguratorCategory categoryDetails) {
        ConfiguratorCategory existingCategory = repository.findById(id).block();
        if (existingCategory == null) {
            return ResponseEntity.notFound().build();
        }
        existingCategory.setName(categoryDetails.getName());
        existingCategory.setIcon(categoryDetails.getIcon());
        ConfiguratorCategory saved = repository.save(existingCategory).block();
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        ConfiguratorCategory existingCategory = repository.findById(id).block();
        if (existingCategory != null) {
            repository.delete(existingCategory).block();
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
