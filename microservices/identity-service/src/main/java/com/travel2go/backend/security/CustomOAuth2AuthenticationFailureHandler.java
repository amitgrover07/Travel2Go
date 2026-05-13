package com.travel2go.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Enumeration;

@Component
@Slf4j
public class CustomOAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        log.error("OAuth2 Authentication Failure: {}", exception.getMessage());
        
        // Log all headers for debugging
        log.debug("--- Request Headers ---");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            log.debug("{}: {}", name, request.getHeader(name));
        }

        // Log all cookies for debugging
        log.debug("--- Request Cookies ---");
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                log.debug("{}: {}", cookie.getName(), cookie.getValue());
            }
        } else {
            log.debug("No cookies found in request");
        }

        String targetUrl = UriComponentsBuilder.fromUriString("https://travel2go.in/login")
                .queryParam("error", true)
                .queryParam("message", exception.getLocalizedMessage())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
