package com.travel2go.agent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent booking mutation - Phase 2+. The route exists so the contract and scope
 * gating are in place now: a key without the {@code book} scope is rejected with
 * 403 by the security layer and never reaches this handler. With the scope, the
 * handler reports 501 until agent booking is enabled.
 */
@RestController
@RequestMapping("/api/agent/v1/bookings")
public class AgentBookingController {

    @PostMapping
    public ResponseEntity<Void> book() {
        // PRD-OPEN: whether agent booking (not just quoting) is enabled in Phase 2,
        // and under what agent-key scopes. Scope gate (SCOPE_book) is already enforced.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
