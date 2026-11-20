package com.travel2go.backend.service;

import com.travel2go.backend.model.Org;
import com.travel2go.backend.service.OrgSpendService.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F6 Business spend controls, against a real Postgres: the approval gate (over-cap
 * bookings held pending, confirmed only after approval), the consolidated monthly
 * bill, and credit-limit enforcement.
 */
@Testcontainers
@SpringBootTest
class OrgSpendIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "test-jwt-secret-0123456789abcdef0123456789abcdef");
    }

    @Autowired private OrgSpendService orgSpendService;

    private final String period = YearMonth.now().toString();

    @Test
    void overCapBooking_isHeldPending_andConfirmsOnlyAfterApproval() {
        Org org = orgSpendService.createOrg("Acme", "admin-1", "ops@acme.test");
        orgSpendService.addMember(org.getId(), "admin-1", "traveller-1", "TRAVELLER");
        orgSpendService.addMember(org.getId(), "admin-1", "appr-1", "APPROVER");
        orgSpendService.setPolicy(org.getId(), "admin-1", 100_000L, 10_000_000L);

        // Under the per-trip cap -> confirms immediately.
        assertThat(orgSpendService.evaluateBooking(org.getId(), "traveller-1", "bk-1", 50_000L))
                .isEqualTo(Decision.APPROVED);

        // Over the per-trip cap -> held pending, NOT in the consolidated bill yet.
        assertThat(orgSpendService.evaluateBooking(org.getId(), "traveller-1", "bk-2", 150_000L))
                .isEqualTo(Decision.PENDING_APPROVAL);
        assertThat(orgSpendService.consolidatedBillPaise(org.getId(), period)).isEqualTo(50_000L);

        // A non-approver cannot approve.
        assertThatThrownBy(() -> orgSpendService.approve(org.getId(), "bk-2", "traveller-1"))
                .isInstanceOf(AccessDeniedException.class);

        // The approver clears it -> now confirmed and billed.
        assertThat(orgSpendService.approve(org.getId(), "bk-2", "appr-1")).isEqualTo(Decision.APPROVED);
        assertThat(orgSpendService.consolidatedBillPaise(org.getId(), period)).isEqualTo(200_000L);
    }

    @Test
    void consolidatedBill_aggregatesTheOrgsBookingsForThePeriod() {
        Org org = orgSpendService.createOrg("Globex", "admin-2", null);
        orgSpendService.addMember(org.getId(), "admin-2", "trav-2", "TRAVELLER");
        orgSpendService.setPolicy(org.getId(), "admin-2", 10_000_000L, 100_000_000L); // high caps: no gate

        orgSpendService.evaluateBooking(org.getId(), "trav-2", "g-1", 120_000L);
        orgSpendService.evaluateBooking(org.getId(), "trav-2", "g-2", 80_000L);
        orgSpendService.evaluateBooking(org.getId(), "trav-2", "g-3", 50_000L);

        assertThat(orgSpendService.consolidatedBillPaise(org.getId(), period)).isEqualTo(250_000L);
        assertThat(orgSpendService.bookingsForPeriod(org.getId(), period)).hasSize(3);
    }

    @Test
    void creditDraws_reduceAvailable_andOverLimitIsRefused() {
        Org org = orgSpendService.createOrg("Initech", "admin-3", null);
        orgSpendService.addMember(org.getId(), "admin-3", "trav-3", "TRAVELLER");
        orgSpendService.setPolicy(org.getId(), "admin-3", 10_000_000L, 100_000_000L); // no approval gate
        orgSpendService.setCreditLimit(org.getId(), "admin-3", 200_000L);

        assertThat(orgSpendService.evaluateBooking(org.getId(), "trav-3", "c-1", 120_000L))
                .isEqualTo(Decision.APPROVED);
        assertThat(orgSpendService.credit(org.getId()).getOutstandingPaise()).isEqualTo(120_000L);

        // Would exceed the limit (120k + 100k > 200k) -> refused, not recorded.
        assertThat(orgSpendService.evaluateBooking(org.getId(), "trav-3", "c-2", 100_000L))
                .isEqualTo(Decision.DENIED);
        assertThat(orgSpendService.credit(org.getId()).getOutstandingPaise()).isEqualTo(120_000L);

        // Exactly to the limit is allowed.
        assertThat(orgSpendService.evaluateBooking(org.getId(), "trav-3", "c-3", 80_000L))
                .isEqualTo(Decision.APPROVED);
        assertThat(orgSpendService.credit(org.getId()).availablePaise()).isZero();
    }

    @Test
    void nonMember_cannotBookAgainstTheOrg() {
        Org org = orgSpendService.createOrg("Hooli", "admin-4", null);
        assertThatThrownBy(() -> orgSpendService.evaluateBooking(org.getId(), "outsider", "x-1", 10_000L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
