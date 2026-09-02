package com.travel2go.backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
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
    public Queue tripPaymentCapturedQueue() {
        return new Queue("trip.payment-captured", true);
    }

    @Bean
    public Binding tripPaymentCapturedBinding() {
        return BindingBuilder.bind(tripPaymentCapturedQueue()).to(tripExchange()).with("payment.captured");
    }
}
