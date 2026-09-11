package com.travel2go.backend.consumer;

import com.travel2go.backend.repository.BookingRepository;
import com.travel2go.backend.repository.LeadRepository;
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
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.awaitility.Awaitility.await;

/**
 * Proves the DLQ + bounded retry wiring end to end against a real RabbitMQ:
 * a message that always fails processing (findByLegId throws) is retried
 * 4 times with backoff, then lands in booking.leg-confirmed.dlq - never
 * silently dropped, never infinitely requeued.
 */
@Testcontainers
@SpringBootTest(properties = {
        "jwt.secret=test-jwt-secret-0123456789abcdef0123456789abcdef",
        "quote.token.secret=test-quote-token-secret-0123456789abcdef0123456789abcdef",
        "spring.cloud.gcp.firestore.enabled=false",
        "spring.cloud.gcp.storage.enabled=false",
        "spring.cloud.gcp.core.enabled=false"
})
class LegConfirmedDlqIntegrationTest {

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
    private BookingRepository bookingRepository;

    // Needed for the full ApplicationContext to load (BookingController depends on it) -
    // same pattern BackendApplicationTests already uses with firestore disabled.
    @MockBean
    private LeadRepository leadRepository;

    @Test
    void unrecoverableMessage_isDeadLetteredAfterExhaustedRetries() {
        when(bookingRepository.findByLegId("leg-dlq-1")).thenReturn(Flux.error(new RuntimeException("always fails")));

        LegConfirmedEvent event = new LegConfirmedEvent("leg-dlq-1", "pay_dlq_1", 150000L);
        rabbitTemplate.convertAndSend("trip.exchange", "leg.confirmed", event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Object dlqMessage = rabbitTemplate.receiveAndConvert("booking.leg-confirmed.dlq", 1000);
            assertThat(dlqMessage).isNotNull();
            assertThat(dlqMessage).isInstanceOf(LegConfirmedEvent.class);
            assertThat(((LegConfirmedEvent) dlqMessage).legId()).isEqualTo("leg-dlq-1");
        });
    }
}
