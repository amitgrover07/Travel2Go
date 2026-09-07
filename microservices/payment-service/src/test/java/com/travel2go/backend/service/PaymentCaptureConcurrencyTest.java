package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.WebhookEvent;
import com.travel2go.backend.provider.WebhookEventType;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.webhook.ProcessedWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "payment.provider=sandbox",
        "razorpay.webhook-secret=test-concurrency-webhook-secret-0123456789"
})
class PaymentCaptureConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @MockBean
    private PaymentProvider paymentProvider;

    @MockBean
    private PaymentEventPublisher eventPublisher;

    private Payment persistCreatedPayment(String bookingRef, String providerOrderId) {
        Payment payment = Payment.builder()
                .bookingRef(bookingRef)
                .method("UPI")
                .status("CREATED")
                .amountPaise(150000L)
                .feePaise(0L)
                .providerRef(providerOrderId)
                .ownerUserId("user-1")
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build();
        return paymentRepository.save(payment);
    }

    @Test
    void concurrentDeliveryOfSameEventCapturesExactlyOnce() throws InterruptedException {
        persistCreatedPayment("leg-race-1", "order_race_1");
        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_race_1", "pay_race_1", 150000L));

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    paymentService.applyWebhook("{}".getBytes(), Map.of());
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

        Payment result = paymentRepository.findByProviderRef("order_race_1").orElseThrow();
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(processedWebhookEventRepository.findById("pay_race_1")).isPresent();
        verify(eventPublisher, times(1)).publish(eq("payment.captured"), any());
    }

    @Test
    void secondEventIdTargetingAlreadyCapturedPaymentIsNoOp() throws InterruptedException {
        Payment payment = persistCreatedPayment("leg-race-2", "order_race_2");
        int firstCapture = paymentRepository.markCaptured(payment.getId(), "pay_first");
        assertThat(firstCapture).isEqualTo(1);

        when(paymentProvider.verifyAndParse(any(), any()))
                .thenReturn(new WebhookEvent(WebhookEventType.CAPTURED, "order_race_2", "pay_second", 150000L));

        paymentService.applyWebhook("{}".getBytes(), Map.of());

        verify(eventPublisher, times(0)).publish(any(), any());
        Payment result = paymentRepository.findByProviderRef("order_race_2").orElseThrow();
        assertThat(result.getProviderPaymentId()).isEqualTo("pay_first");
    }
}
