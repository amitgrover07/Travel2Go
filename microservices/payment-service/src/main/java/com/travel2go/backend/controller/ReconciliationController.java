package com.travel2go.backend.controller;

import com.travel2go.backend.service.ReconciliationService;
import com.travel2go.backend.service.ReconciliationSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @GetMapping
    public ReconciliationSummary summary() {
        return reconciliationService.getSummary();
    }
}
