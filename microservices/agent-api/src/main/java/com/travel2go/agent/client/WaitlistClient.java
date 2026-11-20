package com.travel2go.agent.client;

import com.travel2go.agent.dto.AgentWaitlistRequest;
import com.travel2go.agent.dto.AgentWaitlistResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Wraps the public waitlist-service (F1). Circuit breaker + timeout via the shared
 * Resilience4j config; a failure degrades through {@link WaitlistClientFallback}.
 */
@FeignClient(
        name = "waitlist-service",
        url = "${agent.clients.waitlist-url}",
        fallbackFactory = WaitlistClientFallback.class)
public interface WaitlistClient {

    @PostMapping("/api/v1/waitlist/check")
    AgentWaitlistResponse check(@RequestBody AgentWaitlistRequest request);
}
