package com.travel2go.backend.service;

import com.travel2go.backend.client.DisruptionNotificationClient;
import com.travel2go.backend.client.PaymentRefundClient;
import com.travel2go.backend.model.DisruptionEvent;
import com.travel2go.backend.model.Leg;
import com.travel2go.backend.repository.DisruptionEventRepository;
import com.travel2go.backend.repository.LegRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisruptionServiceTest {

    @Mock private LegRepository legRepository;
    @Mock private DisruptionEventRepository disruptionEventRepository;
    @Mock private RebookingSearch rebookingSearch;
    @Mock private DisruptionNotificationClient notificationClient;
    @Mock private PaymentRefundClient paymentRefundClient;
    @Mock private TripEventPublisher eventPublisher;
    @Mock private QuoteTokenService quoteTokenService;

    private DisruptionService service;

    @BeforeEach
    void setUp() {
        service = new DisruptionService(legRepository, disruptionEventRepository, rebookingSearch,
                notificationClient, paymentRefundClient, eventPublisher, quoteTokenService);
        lenient().when(disruptionEventRepository.findById(anyString())).thenReturn(Mono.empty());
        lenient().when(disruptionEventRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        lenient().when(legRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private Leg confirmedLeg() {
        return Leg.builder().id("leg-1").tripId("trip-1").type("RAIL").status("CONFIRMED").pricePaise(150000L).build();
    }

    @Test
    void rebooksOntoCheaperAlternative_setsConfirmedAndNotifies() {
        Leg leg = confirmedLeg();
        Leg alt = Leg.builder().id("leg-2").tripId("trip-1").type("RAIL").status("SEARCHING")
                .pricePaise(140000L).quoteToken("alt-token").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(rebookingSearch.findAlternative(leg)).thenReturn(Optional.of(alt));
        when(quoteTokenService.isValid("alt-token", "leg-2", 140000L)).thenReturn(true);

        String outcome = service.handleDisruption("leg-1", "evt-1", "DELAY");

        assertThat(outcome).isEqualTo(DisruptionService.REBOOKED);
        assertThat(leg.getStatus()).isEqualTo("CONFIRMED");
        assertThat(leg.getRebookedToLegId()).isEqualTo("leg-2");
        assertThat(leg.getDisruptionReason()).isEqualTo("DELAY");
        assertThat(alt.getStatus()).isEqualTo("CONFIRMED");
        verify(eventPublisher).publish(eq("leg.rebooked"), any());
        verify(notificationClient).sendDisruptionNotice(any());
        verify(paymentRefundClient, never()).refund(any(), any());
    }

    @Test
    void noAlternative_autoRefundsAndNotifies() {
        Leg leg = confirmedLeg();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(rebookingSearch.findAlternative(leg)).thenReturn(Optional.empty());

        String outcome = service.handleDisruption("leg-1", "evt-1", "CANCELLATION");

        assertThat(outcome).isEqualTo(DisruptionService.REFUNDED);
        assertThat(leg.getStatus()).isEqualTo("DISRUPTED");
        verify(paymentRefundClient).refund("leg-1", "DISRUPTION");
        verify(eventPublisher).publish(eq("leg.disrupted_refunded"), any());
        verify(notificationClient).sendDisruptionNotice(any());
    }

    @Test
    void fareIncreasingAlternative_isNotSilentlyCharged() {
        Leg leg = confirmedLeg();
        Leg pricier = Leg.builder().id("leg-3").tripId("trip-1").type("RAIL").status("SEARCHING")
                .pricePaise(250000L).quoteToken("alt-token").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(rebookingSearch.findAlternative(leg)).thenReturn(Optional.of(pricier));
        when(quoteTokenService.isValid("alt-token", "leg-3", 250000L)).thenReturn(true);

        String outcome = service.handleDisruption("leg-1", "evt-1", "DELAY");

        assertThat(outcome).isEqualTo(DisruptionService.CONSENT_REQUIRED);
        assertThat(leg.getStatus()).isEqualTo("DISRUPTED"); // not rebooked, not charged
        assertThat(leg.getRebookedToLegId()).isNull();
        verify(paymentRefundClient, never()).refund(any(), any());
        verify(eventPublisher, never()).publish(eq("leg.rebooked"), any());
        verify(notificationClient).sendDisruptionNotice(any()); // asked for consent
    }

    @Test
    void alreadyHandledDisruption_isNotActedOnAgain() {
        when(disruptionEventRepository.findById("leg-1:evt-1"))
                .thenReturn(Mono.just(DisruptionEvent.builder().outcome(DisruptionService.REFUNDED).build()));

        String outcome = service.handleDisruption("leg-1", "evt-1", "DELAY");

        assertThat(outcome).isEqualTo(DisruptionService.REFUNDED);
        verify(legRepository, never()).findById(anyString());
        verify(paymentRefundClient, never()).refund(any(), any());
        verify(notificationClient, never()).sendDisruptionNotice(any());
    }

    @Test
    void nonConfirmedLeg_isIgnored() {
        Leg leg = confirmedLeg();
        leg.setStatus("SELECTED");
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));

        String outcome = service.handleDisruption("leg-1", "evt-1", "DELAY");

        assertThat(outcome).isEqualTo(DisruptionService.IGNORED);
        verify(rebookingSearch, never()).findAlternative(any());
        verify(paymentRefundClient, never()).refund(any(), any());
    }

    @Test
    void rebook_rejectsAlternativeWithoutValidQuoteToken() {
        Leg leg = confirmedLeg();
        Leg alt = Leg.builder().id("leg-2").tripId("trip-1").type("RAIL")
                .pricePaise(140000L).quoteToken("tampered").build();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(rebookingSearch.findAlternative(leg)).thenReturn(Optional.of(alt));
        when(quoteTokenService.isValid("tampered", "leg-2", 140000L)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.handleDisruption("leg-1", "evt-1", "DELAY"));

        verify(eventPublisher, never()).publish(eq("leg.rebooked"), any());
    }

    @Test
    void recordsDisruptionEventWithOutcome() {
        Leg leg = confirmedLeg();
        when(legRepository.findById("leg-1")).thenReturn(Mono.just(leg));
        when(rebookingSearch.findAlternative(leg)).thenReturn(Optional.empty());

        service.handleDisruption("leg-1", "evt-9", "DELAY");

        ArgumentCaptor<DisruptionEvent> captor = ArgumentCaptor.forClass(DisruptionEvent.class);
        verify(disruptionEventRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("leg-1:evt-9");
        assertThat(captor.getValue().getOutcome()).isEqualTo(DisruptionService.REFUNDED);
    }
}
