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

        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            System.out.println("No valid Authorization header found for URI: " + request.getRequestURI() + 
                               " (Header present: " + (authHeader != null) + ")");
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7).trim();
        System.out.println("Processing token for URI: " + request.getRequestURI() + " (Token length: " + jwt.length() + ")");
        
        try {
            username = jwtUtil.extractUsername(jwt);
            System.out.println("Extracted username: " + username);
            
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> roles = jwtUtil.extractRoles(jwt);
                System.out.println("Extracted roles from token: " + roles);
                
                org.springframework.security.core.userdetails.User userDetails = new org.springframework.security.core.userdetails.User(username, "", roles);
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    System.out.println("Token validation SUCCESS for: " + username);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    System.out.println("Token validation FAILED for user: " + username);
                }
            }
        } catch (Exception e) {
            System.err.println("JWT Authentication Error for URI " + request.getRequestURI() + ": " + e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
