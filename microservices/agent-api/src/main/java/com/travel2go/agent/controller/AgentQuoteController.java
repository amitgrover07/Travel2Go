package com.travel2go.agent.controller;

import com.travel2go.agent.dto.AgentQuoteRequest;
import com.travel2go.agent.dto.AgentQuoteResponse;
import com.travel2go.agent.service.AgentQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/v1/quote")
@RequiredArgsConstructor
public class AgentQuoteController {

    private final AgentQuoteService quoteService;

    @PostMapping
    public ResponseEntity<AgentQuoteResponse> quote(@RequestBody AgentQuoteRequest request) {
        try {
            return ResponseEntity.ok(quoteService.quote(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
