package com.travel2go.agent.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the versioned OpenAPI contract. The spec is a checked-in artifact kept
 * in sync with the controllers by {@code OpenApiContractTest}.
 */
@RestController
public class OpenApiController {

    private static final Resource SPEC = new ClassPathResource("openapi/agent-api-v1.yaml");

    @GetMapping(value = "/api/agent/v1/openapi.yaml", produces = "application/yaml")
    public ResponseEntity<Resource> openapi() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/yaml"))
                .body(SPEC);
    }
}
