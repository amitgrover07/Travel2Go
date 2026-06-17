package com.travel2go.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10; // 10 requests allowed
    private static final long TIME_WINDOW_MS = 60000; // in 1 minute (60,000 milliseconds)

    private final ConcurrentHashMap<String, RequestCounter> limitMap = new ConcurrentHashMap<>();

    private static class RequestCounter {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong timestamp = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Rate limit sensitive endpoints
        if (path.startsWith("/api/auth/login") || 
            path.startsWith("/api/auth/send-login-otp") || 
            path.startsWith("/api/auth/forgot-password") || 
            path.startsWith("/api/auth/reset-password")) {

            String clientIp = getClientIp(request);
            String key = clientIp + ":" + path;

            long now = System.currentTimeMillis();
            RequestCounter counter = limitMap.computeIfAbsent(key, k -> new RequestCounter());

            // Reset counter if time window has passed
            if (now - counter.timestamp.get() > TIME_WINDOW_MS) {
                synchronized (counter) {
                    if (now - counter.timestamp.get() > TIME_WINDOW_MS) {
                        counter.count.set(0);
                        counter.timestamp.set(now);
                    }
                }
            }

            int currentRequests = counter.count.incrementAndGet();

            if (currentRequests > MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
