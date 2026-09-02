package com.travel2go.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public void configureVirtualHost(ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory) {
            CachingConnectionFactory cachingFactory = (CachingConnectionFactory) connectionFactory;
            if ("/".equals(cachingFactory.getVirtualHost()) && !"guest".equals(cachingFactory.getUsername())) {
                System.out.println("Booking-Service: Overriding default virtual host '/' to '" 
                        + cachingFactory.getUsername() + "' for CloudAMQP compatibility");
                cachingFactory.setVirtualHost(cachingFactory.getUsername());
            }
        }
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper typeMapper =
                new org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper();
        // Security: never trust an arbitrary class name from a message's __TypeId__ header
        // (that header is producer-controlled and, with setTrustedPackages("*"), would let any
        // class on this classpath be instantiated via Jackson polymorphic deserialization - a
        // known gadget-chain vector). INFERRED makes the @RabbitListener method's own parameter
        // type authoritative regardless of what the header says; trustedPackages is scoped to
        // just this service's own consumer package as a second layer, and idClassMapping stays
        // only as an explicit, auditable record of the one cross-service type this consumer
        // deliberately accepts (payment-service's PaymentCapturedEvent, mapped to our local copy).
        typeMapper.setTrustedPackages("com.travel2go.backend.consumer");
        typeMapper.setTypePrecedence(
                org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setIdClassMapping(java.util.Map.of(
                "com.travel2go.backend.service.PaymentCapturedEvent",
                com.travel2go.backend.consumer.PaymentCapturedEvent.class));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

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

    // --- P1.2: consumer side of payment-service's trip.exchange fan-out ---

    @Bean
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }

    @Bean
    public Queue bookingPaymentCapturedQueue() {
        return new Queue("booking.payment-captured", true);
    }

    @Bean
    public Binding bookingPaymentCapturedBinding() {
        return BindingBuilder.bind(bookingPaymentCapturedQueue()).to(tripExchange()).with("payment.captured");
    }
}

