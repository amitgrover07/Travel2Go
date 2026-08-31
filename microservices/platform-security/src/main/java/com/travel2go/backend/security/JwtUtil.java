package com.travel2go.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Canonical JWT utility shared by every Travel2Go service (module: platform-security).
 *
 * Reconciled from the four drifted per-service copies. Behaviour preserved:
 *   - HS256 signing, claims {@code role}, {@code name}, {@code picture}
 *   - secret trimming + surrounding-quote stripping (kept from identity/booking/media)
 *   - {@link #generateToken} + full extractor set (kept from identity, the issuer)
 *   - role extraction identical to identity's (singular {@code role} + plural {@code roles},
 *     each normalised to a {@code ROLE_}-prefixed authority)
 *
 * Deliberately removed during reconciliation:
 *   - the hardcoded default-secret fallback in package-service (a security hole:
 *     it silently accepted tokens signed with the public repo secret)
 *   - the JWT-secret fingerprint logging via System.out (leaked the secret prefix)
 *   - the ad-hoc 5-minute clock-skew grace + expiry logging in package-service
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private String effectiveSecret() {
        String s = secret != null ? secret.trim() : "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * Fail fast at startup, not per request. A missing {@code jwt.secret} already
     * fails placeholder resolution; this additionally rejects a present-but-weak
     * key. Never logs the secret itself - only its length.
     */
    @PostConstruct
    void validateSecret() {
        int len = effectiveSecret().getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be set and at least 32 bytes for HS256 (was " + len + "). Refusing to start.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(effectiveSecret().getBytes(StandardCharsets.UTF_8));
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

    /**
     * Preserves identity-service's exact role logic: singular {@code role} plus
     * optional plural {@code roles}, each normalised to a ROLE_-prefixed authority.
     */
    public List<SimpleGrantedAuthority> extractRoles(String token) {
        final Claims claims = extractAllClaims(token);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        String role = claims.get("role", String.class);
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        if (roles != null) {
            for (String r : roles) {
                authorities.add(new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r));
            }
        }
        return authorities;
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}
