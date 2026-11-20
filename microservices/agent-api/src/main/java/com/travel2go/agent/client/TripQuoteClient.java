package com.travel2go.agent.client;

import com.travel2go.backend.dto.LegQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Delegates quoting to trip-service, the price authority. agent-api holds no
 * signing secret and never mints tokens itself - trip-service signs the leg's
 * authoritative price (G2). Circuit breaker + timeout via shared Resilience4j config.
 *
 * PRD-OPEN: service-to-service auth. trip-service's endpoint is user-JWT gated; a
 * dedicated internal/service credential needs wiring here before the live hop works.
 */
@FeignClient(
        name = "trip-service",
        url = "${agent.clients.trip-url}",
        fallbackFactory = TripQuoteClientFallback.class)
public interface TripQuoteClient {

    @GetMapping("/api/trips/legs/{legId}/quote")
    LegQuoteResponse quoteLeg(@PathVariable("legId") String legId);
}
