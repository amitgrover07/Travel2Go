package com.travel2go.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Canonical quote-token service shared by trip-service and payment-service
 * (module: platform-security).
 *
 * Reconciles the two previously divergent per-service copies into one, so the
 * issuer and the validator can never drift apart on the HMAC token they must
 * agree on byte-for-byte. Behaviour preserved exactly:
 *   - HS256 over { legId (subject), pricePaise claim }, TTL-bounded
 *   - {@link #isValid} : signature + legId + exact pricePaise match; expiry
 *     enforced because parsing throws on an expired/tampered token
 *   - {@link #issue} : the superset method (previously only payment-service had
 *     it), so trip-service can now sign at pricing time as the design intends
 *
 * Guarded by {@code @ConditionalOnProperty} so only services that configure
 * {@code quote.token.secret} (trip, payment) instantiate it; the other services
 * that also depend on platform-security are unaffected.
 */
@Service
@ConditionalOnProperty(name = "quote.token.secret")
public class QuoteTokenService {

    @Value("${quote.token.secret}")
    private String secret;

    @Value("${quote.token.ttl-ms:900000}")
    private long ttlMs;

    /**
     * Fail fast at startup: a missing quote.token.secret already fails placeholder
     * resolution; this additionally rejects a present-but-weak key.
     */
    @PostConstruct
    void validateSecret() {
        int len = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "quote.token.secret must be set and at least 32 bytes for HS256 (was " + len + "). Refusing to start.");
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String legId, long pricePaise) {
        Date now = new Date();
        return Jwts.builder()
                .subject(legId)
                .claim("pricePaise", pricePaise)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey())
                .compact();
    }

    public boolean isValid(String token, String legId, long pricePaise) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            boolean legMatches = legId.equals(claims.getSubject());
            Number priceClaim = claims.get("pricePaise", Number.class);
            boolean priceMatches = priceClaim != null && pricePaise == priceClaim.longValue();
            return legMatches && priceMatches;
        } catch (JwtException e) {
            return false;
        }
    }
}
