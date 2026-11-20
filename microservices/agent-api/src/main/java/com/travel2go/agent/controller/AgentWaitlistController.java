package com.travel2go.agent.controller;

import com.travel2go.agent.client.UpstreamUnavailableException;
import com.travel2go.agent.client.WaitlistClient;
import com.travel2go.agent.dto.AgentWaitlistRequest;
import com.travel2go.agent.dto.AgentWaitlistResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agent-facing waitlist probability check - wraps the public F1 service. */
@RestController
@RequestMapping("/api/agent/v1/waitlist/check")
@RequiredArgsConstructor
public class AgentWaitlistController {

    private final WaitlistClient waitlistClient;

    @PostMapping
    public ResponseEntity<AgentWaitlistResponse> check(@RequestBody AgentWaitlistRequest request) {
        try {
            return ResponseEntity.ok(waitlistClient.check(request));
        } catch (UpstreamUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
