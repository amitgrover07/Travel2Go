package com.travel2go.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's autoconfigured ObjectMapper bean is Jackson 3
 * (tools.jackson.databind.ObjectMapper); PaymentService and OutboxRelay are
 * wired against classic Jackson 2 (com.fasterxml.jackson.databind.ObjectMapper)
 * for outbox/webhook payload (de)serialization, so that bean type is no longer
 * autoconfigured for them and needs to be provided explicitly.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
