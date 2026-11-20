package com.travel2go.backend.service;

import com.travel2go.backend.model.Payment;
import com.travel2go.backend.model.Refund;
import com.travel2go.backend.provider.PaymentProvider;
import com.travel2go.backend.provider.RefundResult;
import com.travel2go.backend.repository.OutboxRepository;
import com.travel2go.backend.repository.PaymentRepository;
import com.travel2go.backend.repository.RefundFloatLedgerRepository;
import com.travel2go.backend.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F3 Refund SLA engine, proven against a real Postgres (Testcontainers):
 * SLA timer + trigger, exactly-once under concurrency, ring-fenced float debit/
 * credit + low alert, SLA breach flagging, and settlement.
 */
@Testcontainers
@SpringBootTest(properties = {
        "payment.provider=sandbox",
        "razorpay.webhook-secret=test-refund-sla-webhook-secret-0123456789",
        "payment.refund-float.low-threshold-paise=1000000"
})
class RefundSlaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private RefundFloatLedgerRepository floatLedgerRepository;
    @Autowired private RefundFloatService refundFloatService;
    @Autowired private OutboxRepository outboxRepository;

    @MockitoBean private PaymentProvider paymentProvider;

    private Payment persistCapturedPayment(String bookingRef, String providerPaymentId, long amountPaise) {
        return paymentRepository.save(Payment.builder()
                .bookingRef(bookingRef)
                .method("UPI")
                .status("CAPTURED")
                .amountPaise(amountPaise)
                .feePaise(0L)
                .providerRef("order_" + bookingRef)
                .providerPaymentId(providerPaymentId)
                .ownerUserId("user-1")
                .quoteTokenValidated(true)
                .createdAt(new Date())
                .build());
    }

    private long outboxCount(String bookingRef, String type) {
        return outboxRepository.findAll().stream()
                .filter(e -> bookingRef.equals(e.getAggregateId()) && type.equals(e.getType()))
                .count();
    }

    @Test
    void refund_startsSlaCountdown_recordsTrigger_andDebitsFloat() {
        when(paymentProvider.refund(any(), anyLong())).thenReturn(new RefundResult("SUCCESS", "rfnd_sla_1"));
        Payment payment = persistCapturedPayment("ref-sla-1", "pay_sla_1", 150000L);
        long before = System.currentTimeMillis();

        Payment result = paymentService.refund("ref-sla-1", "USER_CANCEL");

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
        assertThat(result.getFeePaise()).isZero(); // G1

        Refund refund = refundRepository.findFirstByPaymentId(payment.getId()).orElseThrow();
        assertThat(refund.getSlaStatus()).isEqualTo("INITIATED");
        assertThat(refund.getTrigger()).isEqualTo("USER_CANCEL");
        assertThat(refund.getStatus()).isEqualTo("SUCCESS");
        assertThat(refund.getFloatTxnId()).isNotNull();
        long expectedDue = before + 72L * 3600_000L;
        assertThat(refund.getSlaDueAt().getTime()).isBetween(expectedDue - 60_000L, expectedDue + 120_000L);

        // Float debited by exactly the refunded amount, linked to this refund.
        long debits = floatLedgerRepository.findAll().stream()
                .filter(l -> refund.getId().equals(l.getRefundId()) && "DEBIT".equals(l.getDirection()))
                .count();
        assertThat(debits).isEqualTo(1);
        assertThat(outboxCount("ref-sla-1", "payment.refunded")).isEqualTo(1);
    }

    @Test
    void concurrentRefundOfSameBookingSettlesExactlyOnce() throws InterruptedException {
        when(paymentProvider.refund(any(), anyLong())).thenReturn(new RefundResult("SUCCESS", "rfnd_conc"));
        Payment payment = persistCapturedPayment("ref-conc-1", "pay_conc_1", 150000L);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    paymentService.refund("ref-conc-1", "MANUAL");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }));
        }

        ready.await();
        go.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                fail("A concurrent refund threw - losing threads must return normally", e.getCause());
            }
        }

        assertThat(refundRepository.findByPaymentId(payment.getId())).hasSize(1);
        assertThat(outboxCount("ref-conc-1", "payment.refunded")).isEqualTo(1);
        verify(paymentProvider, times(1)).refund(any(), anyLong());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo("REFUNDED");

        Refund refund = refundRepository.findFirstByPaymentId(payment.getId()).orElseThrow();
        long debits = floatLedgerRepository.findAll().stream()
                .filter(l -> refund.getId().equals(l.getRefundId()) && "DEBIT".equals(l.getDirection()))
                .count();
        assertThat(debits).isEqualTo(1); // float debited exactly once
    }

    @Test
    void floatTopUpCredits_andLowAlertFiresBelowThreshold() {
        floatLedgerRepository.deleteAll();

        refundFloatService.credit(500_000L); // below the 1,000,000 threshold
        assertThat(refundFloatService.balancePaise()).isEqualTo(500_000L);
        assertThat(refundFloatService.isLow()).isTrue();

        refundFloatService.credit(600_000L); // now 1,100,000, above threshold
        assertThat(refundFloatService.balancePaise()).isEqualTo(1_100_000L);
        assertThat(refundFloatService.isLow()).isFalse();
    }

    @Test
    void unsettledRefundPastSla_isFlaggedBreached_onceWithEvent() {
        Payment payment = persistCapturedPayment("ref-breach-1", "pay_breach_1", 150000L);
        // An overdue, still-open refund (slaDueAt in the past).
        Date pastDue = new Date(System.currentTimeMillis() - 3600_000L);
        refundRepository.save(Refund.builder()
                .paymentId(payment.getId())
                .amountPaise(150000L)
                .status("SUCCESS")
                .slaStatus("INITIATED")
                .slaDueAt(pastDue)
                .trigger("MANUAL")
                .createdAt(new Date())
                .build());

        paymentService.flagSlaBreaches();
        paymentService.flagSlaBreaches(); // idempotent: no second event

        Refund refund = refundRepository.findFirstByPaymentId(payment.getId()).orElseThrow();
        assertThat(refund.getSlaStatus()).isEqualTo("BREACHED");
        assertThat(outboxCount("ref-breach-1", "payment.refund_sla_breached")).isEqualTo(1);
    }

    @Test
    void settlingARefundStopsTheSlaCountdown() {
        when(paymentProvider.refund(any(), anyLong())).thenReturn(new RefundResult("SUCCESS", "rfnd_settle"));
        Payment payment = persistCapturedPayment("ref-settle-1", "pay_settle_1", 150000L);
        paymentService.refund("ref-settle-1", "MANUAL");

        paymentService.markRefundSettled("ref-settle-1");

        Refund refund = refundRepository.findFirstByPaymentId(payment.getId()).orElseThrow();
        assertThat(refund.getSlaStatus()).isEqualTo("SETTLED");

        // A settled refund is never flagged as breached, even if its due time passes.
        paymentService.flagSlaBreaches();
        assertThat(refundRepository.findFirstByPaymentId(payment.getId()).orElseThrow().getSlaStatus())
                .isEqualTo("SETTLED");
    }
}
