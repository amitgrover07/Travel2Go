package com.travel2go.backend.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the same trip.exchange TopicExchange that trip-service declares.
 * RabbitMQ exchange declaration is idempotent for an identical name+type, so
 * both services declaring it is safe regardless of startup order.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }
}
