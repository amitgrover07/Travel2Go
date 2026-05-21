package com.travel2go.backend.config;

import reactor.rabbitmq.*;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue}")
    private String queue;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    @Bean
    public DirectExchange bookingExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue bookingQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public Binding bookingBinding() {
        return BindingBuilder.bind(bookingQueue()).to(bookingExchange()).with(routingKey);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RabbitMQConfig.class);

    @Bean
    public Sender reactiveSender(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        com.rabbitmq.client.ConnectionFactory nativeFactory = ((org.springframework.amqp.rabbit.connection.CachingConnectionFactory) connectionFactory).getRabbitConnectionFactory();
        log.info("Configuring reactive RabbitMQ Sender: Host={}, Port={}, Username={}, VirtualHost={}, SSL={}",
                nativeFactory.getHost(), nativeFactory.getPort(), nativeFactory.getUsername(),
                nativeFactory.getVirtualHost(), nativeFactory.isSSL());
        return RabbitFlux.createSender(new SenderOptions().connectionFactory(nativeFactory));
    }

    @Bean
    public Receiver reactiveReceiver(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        com.rabbitmq.client.ConnectionFactory nativeFactory = ((org.springframework.amqp.rabbit.connection.CachingConnectionFactory) connectionFactory).getRabbitConnectionFactory();
        log.info("Configuring reactive RabbitMQ Receiver: Host={}, Port={}, Username={}, VirtualHost={}, SSL={}",
                nativeFactory.getHost(), nativeFactory.getPort(), nativeFactory.getUsername(),
                nativeFactory.getVirtualHost(), nativeFactory.isSSL());
        return RabbitFlux.createReceiver(new ReceiverOptions().connectionFactory(nativeFactory));
    }
}
