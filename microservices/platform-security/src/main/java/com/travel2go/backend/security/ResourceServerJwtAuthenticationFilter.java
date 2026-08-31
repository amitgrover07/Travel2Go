package com.travel2go.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless, token-only JWT filter for resource services (platform-security).
 *
 * It trusts the signed token and reads authorities straight from it - no
 * UserDetailsService and no user database. Signature and expiry are enforced
 * because {@link JwtUtil#extractUsername} parses the JWS and throws on an
 * invalid or expired token, which is caught and left unauthenticated so the
 * per-service authorization rules apply.
 *
 * This is the reconciliation of the five identical resource-service filters.
 * It is a plain class (not a {@code @Component}) so it is never auto-registered
 * as a servlet filter; each service constructs it explicitly in its own
 * SecurityConfig via {@code new ResourceServerJwtAuthenticationFilter(jwtUtil)}.
 *
 * identity-service keeps its own UserDetailsService-based filter - a different
 * strategy that is legitimately not shared.
 */
public class ResourceServerJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public ResourceServerJwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        try {
            final String username = jwtUtil.extractUsername(jwt); // throws on bad signature / expiry
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<SimpleGrantedAuthority> authorities = jwtUtil.extractRoles(jwt);
                User principal = new User(username, "", authorities);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            // invalid / expired token -> stay unauthenticated; authorization rules handle it
        }
        filterChain.doFilter(request, response);
    }
}
