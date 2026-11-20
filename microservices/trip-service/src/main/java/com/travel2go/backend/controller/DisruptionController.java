package com.travel2go.backend.controller;

import com.travel2go.backend.client.RefundDispatchException;
import com.travel2go.backend.dto.DisruptionRequest;
import com.travel2go.backend.service.DisruptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin/feed ingestion for disruptions (F4). This is one pluggable disruption
 * source (a manual/admin webhook); a real public status feed is another and reuses
 * the same {@link DisruptionService} entry point. Admin-gated.
 */
@RestController
@RequestMapping("/api/trips/legs")
@RequiredArgsConstructor
public class DisruptionController {

    private final DisruptionService disruptionService;

    @PostMapping("/{legId}/disruptions")
    public ResponseEntity<Map<String, String>> reportDisruption(@PathVariable String legId,
                                                                @RequestBody DisruptionRequest request) {
        if (request.getSourceEventId() == null || request.getSourceEventId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String outcome = disruptionService.handleDisruption(legId, request.getSourceEventId(), request.getReason());
            return ResponseEntity.ok(Map.of("legId", legId, "outcome", outcome));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (RefundDispatchException e) {
            // Could not dispatch the refund now; a retried delivery re-attempts (idempotent).
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
