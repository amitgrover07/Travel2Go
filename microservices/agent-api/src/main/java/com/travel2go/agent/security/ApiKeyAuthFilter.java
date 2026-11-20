package com.travel2go.agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates agents by the {@code X-Agent-Key} header, mapping a known key to
 * its least-privilege scopes as {@code SCOPE_*} authorities. This is entirely
 * separate from the end-user JWT auth used by the internal services.
 *
 * <p>Every authenticated agent request is audited (key id + method + path). A
 * present-but-unknown key is rejected immediately; a missing key is left
 * unauthenticated so the security chain returns 401 for protected routes and
 * still serves the public OpenAPI doc.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Agent-Key";
    private static final Logger AUDIT = LoggerFactory.getLogger("agent.audit");

    private final ApiKeyRegistry registry;

    public ApiKeyAuthFilter(ApiKeyRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);

        if (presented != null && !presented.isBlank()) {
            var resolved = registry.resolve(presented);
            if (resolved.isEmpty()) {
                AUDIT.warn("agent auth rejected: unknown key, method={} path={}",
                        request.getMethod(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
                return;
            }
            ApiKeyRegistry.AgentKey key = resolved.get();
            List<SimpleGrantedAuthority> authorities = key.scopes().stream()
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(key.id(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            AUDIT.info("agent action: keyId={} method={} path={} scopes={}",
                    key.id(), request.getMethod(), request.getRequestURI(), key.scopes());
        }

        chain.doFilter(request, response);
    }
}
