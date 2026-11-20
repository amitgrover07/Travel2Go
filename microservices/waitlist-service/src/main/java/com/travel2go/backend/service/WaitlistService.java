package com.travel2go.backend.service;

import com.travel2go.backend.dto.WaitlistCheckRequest;
import com.travel2go.backend.dto.WaitlistCheckResponse;
import com.travel2go.backend.model.WaitlistCheck;
import com.travel2go.backend.model.WaitlistWatch;
import com.travel2go.backend.repository.WaitlistCheckRepository;
import com.travel2go.backend.repository.WaitlistWatchRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Orchestrates a public waitlist check (F1): validate, estimate, persist (no PII),
 * and return a shareable result. Keeps the estimator pure by owning all I/O here.
 *
 * <p>Security posture (hardened after review):
 * <ul>
 *   <li>All client-supplied fields are bounds-checked / allow-listed before persistence,
 *       so an anonymous caller cannot abuse Firestore as unbounded storage.</li>
 *   <li>The source IP is pseudonymised with a keyed HMAC under a required secret (no
 *       insecure default), so stored values are not reversible via a known static salt.
 *       The hash is still treated as PII for retention.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private static final Pattern TRAIN_NO = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    private static final Pattern CLASS_CODE = Pattern.compile("^[A-Za-z0-9]{1,4}$");
    private static final int MAX_POSITION = 100_000;
    private static final int MAX_CONTACT_LEN = 120;
    private static final Set<String> ALLOWED_CHANNELS = Set.of("WHATSAPP", "EMAIL", "SMS");

    private final WaitlistEstimator estimator;
    private final WaitlistCheckRepository checkRepository;
    private final WaitlistWatchRepository watchRepository;

    @Value("${waitlist.share.base-url:https://travel2go.in/waitlist}")
    private String shareBaseUrl;

    /** Required secret - no default; a weak/absent key fails startup rather than silently pseudonymising. */
    @Value("${waitlist.ip-hash.secret}")
    private String ipHashSecret;

    @PostConstruct
    void validateSecret() {
        int len = ipHashSecret == null ? 0 : ipHashSecret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "waitlist.ip-hash.secret must be set and at least 32 bytes (was " + len + "). Refusing to start.");
        }
    }

    public WaitlistCheckResponse check(WaitlistCheckRequest request, String rawClientIp) {
        String trainNo = requireMatch(request.getTrainNo(), TRAIN_NO, "trainNo");
        String classCode = requireMatch(request.getClassCode(), CLASS_CODE, "classCode");
        int position = validatePosition(request.getCurrentPosition());
        LocalDate travelDate = parseDate(request.getTravelDate());

        WaitlistEstimator.Estimate estimate = estimator.estimate(trainNo, travelDate, classCode, position);

        String shareToken = UUID.randomUUID().toString().replace("-", "");

        WaitlistCheck check = WaitlistCheck.builder()
                .trainNo(trainNo)
                .travelDate(request.getTravelDate())
                .classCode(classCode)
                .currentPosition(position)
                .estimateProbability(estimate.probability())
                .verdict(estimate.verdict())
                .explanation(estimate.explanation())
                .sourceIp(hashIp(rawClientIp))
                .shareToken(shareToken)
                .createdAt(new Date())
                .build();

        checkRepository.save(check).block();

        return toResponse(check);
    }

    public WaitlistCheckResponse getByShareToken(String shareToken) {
        WaitlistCheck check = checkRepository.findByShareToken(shareToken).block();
        if (check == null) {
            throw new IllegalArgumentException("No waitlist check for shareToken " + shareToken);
        }
        return toResponse(check);
    }

    /** Store a notify request against a shared result without requiring an account. */
    public void watch(String shareToken, String contact, String channel) {
        String validatedChannel = validateChannel(channel);
        String validatedContact = validateContact(contact);
        // Validate the token refers to a real check (also 404s an unknown token).
        getByShareToken(shareToken);
        WaitlistWatch watch = WaitlistWatch.builder()
                .shareToken(shareToken)
                .contact(validatedContact)
                .channel(validatedChannel)
                .status("PENDING")
                .createdAt(new Date())
                .build();
        watchRepository.save(watch).block();
    }

    private WaitlistCheckResponse toResponse(WaitlistCheck check) {
        return WaitlistCheckResponse.builder()
                .probability(check.getEstimateProbability())
                .verdict(check.getVerdict())
                .explanation(check.getExplanation())
                .shareToken(check.getShareToken())
                .shareUrl(shareBaseUrl + "/" + check.getShareToken())
                .build();
    }

    private String requireMatch(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new InvalidWaitlistInputException(field + " is missing or malformed");
        }
        return value;
    }

    private int validatePosition(Integer position) {
        if (position == null || position < 0 || position > MAX_POSITION) {
            throw new InvalidWaitlistInputException("currentPosition must be between 0 and " + MAX_POSITION);
        }
        return position;
    }

    private String validateChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String normalised = channel.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_CHANNELS.contains(normalised)) {
            throw new InvalidWaitlistInputException("channel must be one of " + ALLOWED_CHANNELS);
        }
        return normalised;
    }

    private String validateContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return null;
        }
        String trimmed = contact.trim();
        if (trimmed.length() > MAX_CONTACT_LEN) {
            throw new InvalidWaitlistInputException("contact exceeds " + MAX_CONTACT_LEN + " characters");
        }
        return trimmed;
    }

    private LocalDate parseDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidWaitlistInputException("travelDate must be an ISO date (yyyy-MM-dd)");
        }
    }

    private String hashIp(String rawClientIp) {
        if (rawClientIp == null || rawClientIp.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(ipHashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawClientIp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            // HMAC-SHA256 is always present on a standard JRE; never store the raw IP as a fallback.
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
