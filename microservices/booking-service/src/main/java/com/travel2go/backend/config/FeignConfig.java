package com.travel2go.backend.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Propagates the caller's Authorization header onto outbound Feign requests so
 * downstream services see the same JWT. (B5: replaced System.out/err with SLF4J -
 * the previous prints were noise in production logs.)
 */
@Configuration
public class FeignConfig implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignConfig.class);

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("No request context available; cannot propagate Authorization header to {}", template.url());
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null) {
            template.header("Authorization", authorizationHeader);
            log.debug("Propagated Authorization header to {}", template.url());
        } else {
            log.warn("No Authorization header on incoming request; downstream call to {} will be unauthenticated",
                    template.url());
        }
    }
}
