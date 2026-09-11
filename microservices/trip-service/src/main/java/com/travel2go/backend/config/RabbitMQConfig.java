package com.travel2go.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Autowired
    public void configureVirtualHost(ConnectionFactory connectionFactory) {
        if (connectionFactory instanceof CachingConnectionFactory) {
            CachingConnectionFactory cachingFactory = (CachingConnectionFactory) connectionFactory;
            if ("/".equals(cachingFactory.getVirtualHost()) && !"guest".equals(cachingFactory.getUsername())) {
                cachingFactory.setVirtualHost(cachingFactory.getUsername());
            }
        }
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        // Security: never trust an arbitrary class name from a message's __TypeId__ header
        // (that header is producer-controlled and, with setTrustedPackages("*"), would let any
        // class on this classpath be instantiated via Jackson polymorphic deserialization - a
        // known gadget-chain vector). INFERRED makes the @RabbitListener method's own parameter
        // type authoritative regardless of what the header says; trustedPackages is scoped to
        // just this service's own consumer package as a second layer, and idClassMapping stays
        // only as an explicit, auditable record of the one cross-service type this consumer
        // deliberately accepts (payment-service's PaymentCapturedEvent, mapped to our local copy).
        // Also used for outbound serialization now (P1.4): TripEventPublisher reuses this same
        // converter bean to serialize outgoing LegConfirmedEvents, not just incoming ones.
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
    public TopicExchange tripExchange() {
        return new TopicExchange("trip.exchange");
    }

    @Bean
    public FanoutExchange tripDlx() {
        return new FanoutExchange("trip.dlx");
    }

    @Bean
    public Queue tripPaymentCapturedDlq() {
        return new Queue("trip.payment-captured.v2.dlq", true);
    }

    @Bean
    public Binding tripPaymentCapturedDlqBinding() {
        return BindingBuilder.bind(tripPaymentCapturedDlq()).to(tripDlx());
    }

    // Renamed from trip.payment-captured (P1.4): adding the x-dead-letter-exchange argument to
    // the pre-existing queue would fail RabbitMQ's redeclare-with-different-arguments check
    // against the real broker; a fresh name avoids that collision.
    @Bean
    public Queue tripPaymentCapturedQueue() {
        return QueueBuilder.durable("trip.payment-captured.v2")
                .withArgument("x-dead-letter-exchange", "trip.dlx")
                .build();
    }

    @Bean
    public Binding tripPaymentCapturedBinding() {
        return BindingBuilder.bind(tripPaymentCapturedQueue()).to(tripExchange()).with("payment.captured");
    }
}
