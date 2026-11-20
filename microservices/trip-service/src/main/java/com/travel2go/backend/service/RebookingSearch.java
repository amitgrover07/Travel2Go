package com.travel2go.backend.service;

import com.travel2go.backend.model.Leg;

import java.util.Optional;

/**
 * Pluggable search for the best alternative leg when a confirmed leg is disrupted
 * (the rebooking half of the pluggable disruption ingestion in the PRD). A real
 * supplier/inventory feed - with its own circuit breaker + timeout - drops in here
 * later; the default returns no alternative so a disruption with no option safely
 * falls through to an auto-refund.
 */
public interface RebookingSearch {

    /** @return a price-locked alternative leg on the same route/day, if one is available. */
    Optional<Leg> findAlternative(Leg disruptedLeg);
}
