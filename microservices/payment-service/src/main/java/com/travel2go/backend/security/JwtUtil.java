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
import java.util.List;
import java.util.function.Function;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public List<SimpleGrantedAuthority> extractRoles(String token) {
        final Claims claims = extractAllClaims(token);
        java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();

        String role = claims.get("role", String.class);
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
        }

        java.util.List<String> roles = claims.get("roles", java.util.List.class);
        if (roles != null) {
            for (String r : roles) {
                authorities.add(new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r));
            }
        }
        return authorities;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
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
