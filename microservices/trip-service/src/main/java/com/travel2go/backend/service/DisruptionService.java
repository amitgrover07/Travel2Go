package com.travel2go.backend.service;

import com.travel2go.backend.client.DisruptionNotificationClient;
import com.travel2go.backend.client.PaymentRefundClient;
import com.travel2go.backend.model.DisruptionEvent;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.repository.DisruptionEventRepository;
import com.travel2go.backend.repository.LegRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * The rescue moment (F4): on a qualifying disruption of a confirmed leg, mark it
 * DISRUPTED and either auto-rebook onto the best alternative (and notify "we've
 * already rebooked you") or, if there is no alternative, auto-trigger an F3 refund
 * and notify. Idempotent per (legId, sourceEventId): the same disruption is handled
 * exactly once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisruptionService {

    public static final String REBOOKED = "REBOOKED";
    public static final String REFUNDED = "REFUNDED";
    public static final String CONSENT_REQUIRED = "CONSENT_REQUIRED";
    public static final String IGNORED = "IGNORED";

    private final LegRepository legRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final RebookingSearch rebookingSearch;
    private final DisruptionNotificationClient notificationClient;
    private final PaymentRefundClient paymentRefundClient;
    private final TripEventPublisher eventPublisher;
    private final QuoteTokenService quoteTokenService;

    public String handleDisruption(String legId, String sourceEventId, String reason) {
        String eventKey = legId + ":" + sourceEventId;

        DisruptionEvent existing = disruptionEventRepository.findById(eventKey).block();
        if (existing != null) {
            // Already handled this exact disruption - do not act again.
            log.info("Disruption {} already handled (outcome {})", eventKey, existing.getOutcome());
            return existing.getOutcome();
        }

        Leg leg = legRepository.findById(legId).block();
        if (leg == null) {
            throw new IllegalArgumentException("Leg not found: " + legId);
        }

        // Only a confirmed leg is a qualifying disruption.
        if (!"CONFIRMED".equals(leg.getStatus())) {
            recordEvent(eventKey, legId, sourceEventId, reason, IGNORED, null);
            log.info("Leg {} is not CONFIRMED (status {}) - ignoring disruption", legId, leg.getStatus());
            return IGNORED;
        }

        Date now = new Date();
        leg.setStatus("DISRUPTED");
        leg.setDisruptionReason(reason);
        leg.setDisruptionHandledAt(now);

        Optional<Leg> alternative = rebookingSearch.findAlternative(leg);

        String outcome;
        String rebookedToLegId = null;

        if (alternative.isPresent()) {
            Leg alt = alternative.get();
            assertPriceLocked(alt); // G2: the alternative must carry a valid quote-token

            if (alt.getPricePaise() <= leg.getPricePaise()) {
                // Fare-neutral or cheaper: auto-rebook (Family+ absorbs fare-neutral rebooks).
                alt.setStatus("CONFIRMED");
                legRepository.save(alt).block();

                leg.setRebookedToLegId(alt.getId());
                leg.setStatus("CONFIRMED"); // the journey is whole again
                rebookedToLegId = alt.getId();
                outcome = REBOOKED;

                eventPublisher.publish("leg.rebooked", Map.of(
                        "legId", legId, "rebookedToLegId", alt.getId()));
                notify(leg, REBOOKED, rebookedToLegId);
            } else {
                // Fare increases: never silently charge more - ask for consent, stay DISRUPTED.
                outcome = CONSENT_REQUIRED;
                notify(leg, CONSENT_REQUIRED, alt.getId());
            }
        } else {
            // No alternative: auto-refund (F3), stay DISRUPTED.
            paymentRefundClient.refund(legId, "DISRUPTION");
            outcome = REFUNDED;
            eventPublisher.publish("leg.disrupted_refunded", Map.of("legId", legId));
            notify(leg, REFUNDED, null);
        }

        legRepository.save(leg).block();
        recordEvent(eventKey, legId, sourceEventId, reason, outcome, rebookedToLegId);
        return outcome;
    }

    private void assertPriceLocked(Leg alt) {
        if (alt.getPricePaise() == null || alt.getQuoteToken() == null
                || !quoteTokenService.isValid(alt.getQuoteToken(), alt.getId(), alt.getPricePaise())) {
            throw new IllegalStateException("Alternative leg " + alt.getId() + " is not price-locked (G2)");
        }
    }

    private void notify(Leg leg, String outcome, String rebookedToLegId) {
        notificationClient.sendDisruptionNotice(new DisruptionNotificationClient.DisruptionNoticeRequest(
                leg.getId(), leg.getType(), outcome, rebookedToLegId, null));
    }

    private void recordEvent(String eventKey, String legId, String sourceEventId, String reason,
                             String outcome, String rebookedToLegId) {
        disruptionEventRepository.save(DisruptionEvent.builder()
                .id(eventKey)
                .legId(legId)
                .sourceEventId(sourceEventId)
                .reason(reason)
                .outcome(outcome)
                .rebookedToLegId(rebookedToLegId)
                .handledAt(new Date())
                .build()).block();
    }
}
