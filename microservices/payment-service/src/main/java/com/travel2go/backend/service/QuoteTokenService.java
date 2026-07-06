package com.travel2go.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class QuoteTokenService {

    @Value("${quote.token.secret}")
    private String secret;

    @Value("${quote.token.ttl-ms}")
    private long ttlMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String legId, long pricePaise) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(legId)
                .claim("pricePaise", pricePaise)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
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
