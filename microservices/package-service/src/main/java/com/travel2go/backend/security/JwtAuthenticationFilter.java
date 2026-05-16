package com.travel2go.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("No Bearer token found in request to: " + request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtUtil.extractUsername(jwt);
            System.out.println("Extracted username: " + username + " from token for URI: " + request.getRequestURI());
            
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> roles = jwtUtil.extractRoles(jwt);
                System.out.println("Extracted roles: " + roles);
                
                org.springframework.security.core.userdetails.User userDetails = new org.springframework.security.core.userdetails.User(username, "", roles);
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    System.out.println("Token validated successfully for user: " + username);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    System.out.println("Token validation failed for user: " + username);
                }
            }
        } catch (Exception e) {
            System.err.println("JWT Authentication Error: " + e.getMessage());
            e.printStackTrace();
        }
        filterChain.doFilter(request, response);
    }
}
