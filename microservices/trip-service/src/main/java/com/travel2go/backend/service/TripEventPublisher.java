package com.travel2go.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripEventPublisher {

    public static final String EXCHANGE = "trip.exchange";

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, payload);
    }
}
