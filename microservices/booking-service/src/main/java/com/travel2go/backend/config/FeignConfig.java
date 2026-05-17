package com.travel2go.backend.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authorizationHeader = request.getHeader("Authorization");
            System.out.println("[FeignConfig] Incoming Request Auth Header: " + (authorizationHeader != null ? "PRESENT" : "NULL"));
            if (authorizationHeader != null) {
                template.header("Authorization", authorizationHeader);
                System.out.println("[FeignConfig] Propagated Authorization Header to Feign Request: " + template.url());
            } else {
                System.err.println("[FeignConfig] WARNING: No Authorization header found on incoming request!");
            }
        } else {
            System.err.println("[FeignConfig] WARNING: RequestContextHolder returned null attributes!");
        }
    }
}
