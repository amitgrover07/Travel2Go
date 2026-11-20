package com.travel2go.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Public widget needs to call this service from the browser. No credentials are
 * used (login-free), so a plain CORS allowance over the versioned API is enough.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/waitlist/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "https://travel2go-495007.web.app",
                        "https://travel2go.in")
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
