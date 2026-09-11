package com.travel2go.backend.consumer;

import com.travel2go.backend.repository.LegRepository;
import com.travel2go.backend.repository.TripRepository;
import com.travel2go.backend.service.TripEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.awaitility.Awaitility.await;

/**
 * Proves trip-service's DLQ + bounded retry wiring end to end against a
 * real RabbitMQ, mirroring booking-service's LegConfirmedDlqIntegrationTest.
 */
@Testcontainers
@SpringBootTest(properties = {
        "jwt.secret=test-jwt-secret-0123456789abcdef0123456789abcdef",
        "quote.token.secret=test-quote-token-secret-0123456789abcdef0123456789abcdef",
        "spring.cloud.gcp.firestore.enabled=false",
        "spring.cloud.gcp.storage.enabled=false",
        "spring.cloud.gcp.core.enabled=false"
})
class PaymentCapturedDlqIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private LegRepository legRepository;

    // Needed for the full ApplicationContext to load (TripService/TripController
    // depend on it) - same pattern TripServiceApplicationTests already uses with
    // firestore disabled (it mocks both TripRepository and LegRepository).
    @MockBean
    private TripRepository tripRepository;

    @MockBean
    private TripEventPublisher tripEventPublisher;

    @Test
    void unrecoverableMessage_isDeadLetteredAfterExhaustedRetries() {
        when(legRepository.findById("leg-dlq-1")).thenReturn(Mono.error(new RuntimeException("always fails")));

        PaymentCapturedEvent event = new PaymentCapturedEvent("leg-dlq-1", "pay_dlq_1", 150000L);
        rabbitTemplate.convertAndSend("trip.exchange", "payment.captured", event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Object dlqMessage = rabbitTemplate.receiveAndConvert("trip.payment-captured.v2.dlq", 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(dlqMessage).isInstanceOf(PaymentCapturedEvent.class);
            assertThat(((PaymentCapturedEvent) dlqMessage).bookingRef()).isEqualTo("leg-dlq-1");
        });
    }
}
