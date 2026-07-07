package com.travel2go.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class QuoteTokenService {

    @Value("${quote.token.secret}")
    private String secret;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isValid(String token, String legId, long pricePaise) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            boolean legMatches = legId.equals(claims.getSubject());
            Number priceClaim = claims.get("pricePaise", Number.class);
            boolean priceMatches = priceClaim != null && pricePaise == priceClaim.longValue();
            return legMatches && priceMatches;
        } catch (JwtException e) {
            return false;
        }
    }
}
