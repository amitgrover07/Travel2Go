package com.travel2go.backend.service;

import com.travel2go.backend.model.Leg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default v1 rebooking search: no live inventory feed is wired yet, so it returns
 * no alternative and a disruption falls through to an auto-refund (the safe
 * outcome). A real external feed replaces this bean and is where the circuit
 * breaker + timeout belong.
 *
 * PRD-OPEN: the real data source for disruption/rebooking inventory.
 */
@Slf4j
@Component
public class DefaultRebookingSearch implements RebookingSearch {

    @Override
    public Optional<Leg> findAlternative(Leg disruptedLeg) {
        log.info("No rebooking inventory feed configured - no alternative for leg {}", disruptedLeg.getId());
        return Optional.empty();
    }
}
