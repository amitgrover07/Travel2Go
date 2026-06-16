package com.travel2go.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        String trimmedSecret = secret != null ? secret.trim() : "";
        if (trimmedSecret.startsWith("\"") && trimmedSecret.endsWith("\"")) {
            trimmedSecret = trimmedSecret.substring(1, trimmedSecret.length() - 1);
        }
        
        if (trimmedSecret.length() > 4) {
            System.out.println("JWT Secret Fingerprint: " + trimmedSecret.substring(0, 4) + "... (Length: " + trimmedSecret.length() + ")");
        } else {
            System.out.println("JWT Secret is too short! Length: " + trimmedSecret.length());
        }

        byte[] keyBytes = trimmedSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, String role, String name, String picture) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .claim("name", name)
                .claim("picture", picture)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            System.err.println("Primary secret failed, trying default fallback... Error: " + e.getMessage());
            // Fail-safe: Try the hardcoded default secret if the environment one fails
            byte[] defaultKeyBytes = "94a08da1fecbb6e8b46990538c7b50b294a08da1fecbb6e8b46990538c7b50b2".getBytes(StandardCharsets.UTF_8);
            SecretKey defaultKey = Keys.hmacShaKeyFor(defaultKeyBytes);
            return Jwts.parserBuilder()
                    .setSigningKey(defaultKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        }
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}
