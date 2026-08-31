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
 * B6: fail fast if the required downstream URL is missing/malformed.
 * identity-service calls notification (OTP delivery). URLs are not secrets.
 */
@Component
public class DownstreamUrlValidator implements ApplicationRunner {

    private static final String SERVICE = "identity-service";

    private final Map<String, String> requiredUrls = new LinkedHashMap<>();

    public DownstreamUrlValidator(@Value("${NOTIFICATION_SERVICE_URL:}") String notificationUrl) {
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
