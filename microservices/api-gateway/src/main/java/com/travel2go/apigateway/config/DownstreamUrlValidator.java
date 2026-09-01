package com.travel2go.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B6: fail fast if any route target URL is missing/malformed, instead of the
 * gateway silently routing to a localhost default and returning errors at
 * request time. The gateway routes to identity, package, booking, media,
 * trip, and (P1.1) the payment-service webhook path.
 */
@Component
public class DownstreamUrlValidator implements ApplicationRunner {

    private static final String SERVICE = "api-gateway";

    private final Map<String, String> requiredUrls = new LinkedHashMap<>();

    public DownstreamUrlValidator(
            @Value("${IDENTITY_SERVICE_URL:}") String identityUrl,
            @Value("${PACKAGE_SERVICE_URL:}") String packageUrl,
            @Value("${BOOKING_SERVICE_URL:}") String bookingUrl,
            @Value("${MEDIA_SERVICE_URL:}") String mediaUrl,
            @Value("${TRIP_SERVICE_URL:}") String tripUrl,
            @Value("${PAYMENT_SERVICE_URL:}") String paymentUrl) {
        requiredUrls.put("IDENTITY_SERVICE_URL", identityUrl);
        requiredUrls.put("PACKAGE_SERVICE_URL", packageUrl);
        requiredUrls.put("BOOKING_SERVICE_URL", bookingUrl);
        requiredUrls.put("MEDIA_SERVICE_URL", mediaUrl);
        requiredUrls.put("TRIP_SERVICE_URL", tripUrl);
        requiredUrls.put("PAYMENT_SERVICE_URL", paymentUrl);
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();
        requiredUrls.forEach((name, value) -> {
            if (value == null || value.isBlank()) {
                problems.add(name + " is blank");
                return;
            }
            try {
                URI u = URI.create(value.trim());
                boolean okScheme = "http".equals(u.getScheme()) || "https".equals(u.getScheme());
                if (!okScheme || u.getHost() == null) {
                    problems.add(name + "=" + value + " is not a valid http(s) URL");
                }
            } catch (IllegalArgumentException e) {
                problems.add(name + "=" + value + " is not a parseable URL");
            }
        });
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    SERVICE + " refusing to start - invalid route URL config: "
                            + String.join("; ", problems));
        }
    }
}
