package com.travel2go.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel2go.backend.model.OutboxEntry;
import com.travel2go.backend.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(properties = {
        "payment.provider=sandbox",
        "razorpay.webhook-secret=test-relay-webhook-secret-0123456789"
})
class OutboxRelayTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentEventPublisher eventPublisher;

    // OutboxRelay's own @Scheduled trigger (fixedDelay=1500ms, no initial
    // delay) fires in the background the moment this context is up,
    // racing the manual outboxRelay.relay() calls below for the same
    // outbox rows and mock invocations - observed to make this test flaky
    // under a warm JVM (e.g. as part of the full `mvnw verify` run) even
    // though it passes reliably in isolation. Mocking TaskScheduler makes
    // Spring's ScheduledAnnotationBeanPostProcessor schedule against a
    // no-op, so relay() only ever runs when this test calls it directly -
    // exactly the "callable directly from tests for deterministic
    // testing" contract OutboxRelay.relay() is designed around. Production
    // wiring is untouched: PaymentServiceApplication provides no
    // TaskScheduler bean of its own, so this override is test-only.
    @MockitoBean
    private TaskScheduler taskScheduler;

    private OutboxEntry unpublishedEntry(String aggregateId) throws Exception {
        PaymentCapturedEvent event = new PaymentCapturedEvent(aggregateId, "pay_relay_1", 150000L);
        return outboxRepository.save(OutboxEntry.builder()
                .aggregateId(aggregateId)
                .type("payment.captured")
                .payload(objectMapper.writeValueAsString(event))
                .createdAt(new Date())
                .attempts(0)
                .build());
    }

    @Test
    void relay_publishesUnpublishedEntryAndMarksItPublished() throws Exception {
        OutboxEntry entry = unpublishedEntry("leg-relay-1");

        outboxRelay.relay();

        verify(eventPublisher, times(1)).publish(eq("payment.captured"),
                eq(new PaymentCapturedEvent("leg-relay-1", "pay_relay_1", 150000L)));
        OutboxEntry reloaded = outboxRepository.findById(entry.getId()).orElseThrow();
        assertThat(reloaded.getPublishedAt()).isNotNull();
    }

    @Test
    void relay_skipsAlreadyPublishedEntries() throws Exception {
        OutboxEntry entry = unpublishedEntry("leg-relay-2");
        entry.setPublishedAt(new Date());
        outboxRepository.save(entry);

        outboxRelay.relay();

        verify(eventPublisher, times(0)).publish(any(), any());
    }

    @Test
    void relay_concurrentTicksOnSameRowPublishOnlyOnce() throws Exception {
        unpublishedEntry("leg-relay-3");

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    outboxRelay.relay();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        go.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }
}
