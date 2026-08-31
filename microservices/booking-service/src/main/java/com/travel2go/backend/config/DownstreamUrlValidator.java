package com.travel2go.backend.config;

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
 * B6: fail fast at startup if a required downstream *_SERVICE_URL is missing or
 * malformed, instead of silently pointing at a localhost default and 500-ing at
 * request time (the historical trip-tbd / missing-URL outage).
 *
 * booking-service calls identity (settings), package, and notification.
 * URLs are not secrets, so the offending name/value is logged for debuggability.
 */
@Component
public class DownstreamUrlValidator implements ApplicationRunner {

    private static final String SERVICE = "booking-service";

    private final Map<String, String> requiredUrls = new LinkedHashMap<>();

    public DownstreamUrlValidator(
            @Value("${IDENTITY_SERVICE_URL:}") String identityUrl,
            @Value("${PACKAGE_SERVICE_URL:}") String packageUrl,
            @Value("${NOTIFICATION_SERVICE_URL:}") String notificationUrl) {
        requiredUrls.put("IDENTITY_SERVICE_URL", identityUrl);
        requiredUrls.put("PACKAGE_SERVICE_URL", packageUrl);
        requiredUrls.put("NOTIFICATION_SERVICE_URL", notificationUrl);
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
                    SERVICE + " refusing to start - invalid downstream URL config: "
                            + String.join("; ", problems));
        }
    }
}
