package com.travel2go.backend.service;

import com.travel2go.backend.dto.WaitlistCheckRequest;
import com.travel2go.backend.dto.WaitlistCheckResponse;
import com.travel2go.backend.model.WaitlistCheck;
import com.travel2go.backend.model.WaitlistWatch;
import com.travel2go.backend.repository.WaitlistCheckRepository;
import com.travel2go.backend.repository.WaitlistWatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Orchestrates a public waitlist check (F1): estimate, persist (no PII), and
 * return a shareable result. Keeps the estimator pure by owning all I/O here.
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistEstimator estimator;
    private final WaitlistCheckRepository checkRepository;
    private final WaitlistWatchRepository watchRepository;

    @Value("${waitlist.share.base-url:https://travel2go.in/waitlist}")
    private String shareBaseUrl;

    @Value("${waitlist.ip-hash.salt:travel2go-waitlist}")
    private String ipHashSalt;

    public WaitlistCheckResponse check(WaitlistCheckRequest request, String rawClientIp) {
        LocalDate travelDate = parseDate(request.getTravelDate());
        int position = request.getCurrentPosition() == null ? 0 : request.getCurrentPosition();

        WaitlistEstimator.Estimate estimate =
                estimator.estimate(request.getTrainNo(), travelDate, request.getClassCode(), position);

        String shareToken = UUID.randomUUID().toString().replace("-", "");

        WaitlistCheck check = WaitlistCheck.builder()
                .trainNo(request.getTrainNo())
                .travelDate(request.getTravelDate())
                .classCode(request.getClassCode())
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
        // Validate the token refers to a real check (also 404s an unknown token).
        getByShareToken(shareToken);
        WaitlistWatch watch = WaitlistWatch.builder()
                .shareToken(shareToken)
                .contact(contact)
                .channel(channel)
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

    private LocalDate parseDate(String isoDate) {
        try {
            return LocalDate.parse(isoDate);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("travelDate must be an ISO date (yyyy-MM-dd)");
        }
    }

    private String hashIp(String rawClientIp) {
        if (rawClientIp == null || rawClientIp.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ipHashSalt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(rawClientIp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always present on a standard JRE; never store the raw IP as a fallback.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
