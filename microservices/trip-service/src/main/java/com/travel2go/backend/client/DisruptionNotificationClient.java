package com.travel2go.backend.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Proactively notifies the traveller of a disruption outcome (rebooked / refunded /
 * consent-needed). Circuit breaker + timeout via the shared Resilience4j config;
 * a failure degrades through {@link DisruptionNotificationClientFallbackFactory}
 * so a notification outage never blocks the rescue flow.
 */
@FeignClient(name = "notification-service", url = "${NOTIFICATION_SERVICE_URL:http://localhost:8085}",
        contextId = "disruptionNotificationClient",
        fallbackFactory = DisruptionNotificationClientFallbackFactory.class)
public interface DisruptionNotificationClient {

    @PostMapping("/api/notifications/disruption")
    void sendDisruptionNotice(@RequestBody DisruptionNoticeRequest request);

    class DisruptionNoticeRequest {
        public String legId;
        public String legType;
        public String outcome;          // REBOOKED | REFUNDED | CONSENT_REQUIRED
        public String rebookedToLegId;  // set when REBOOKED
        public String email;            // optional

        public DisruptionNoticeRequest() {}

        public DisruptionNoticeRequest(String legId, String legType, String outcome,
                                       String rebookedToLegId, String email) {
            this.legId = legId;
            this.legType = legType;
            this.outcome = outcome;
            this.rebookedToLegId = rebookedToLegId;
            this.email = email;
        }
    }
}
