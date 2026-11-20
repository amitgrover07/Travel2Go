package com.travel2go.backend.repository;

import com.travel2go.backend.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByPaymentId(UUID paymentId);

    Optional<Refund> findFirstByPaymentId(UUID paymentId);

    @Query("SELECT COALESCE(SUM(r.amountPaise), 0) FROM Refund r WHERE r.status = 'SUCCESS'")
    long sumRefundedAmountPaise();

    /**
     * Atomically claims the single refund slot for a payment via a native
     * INSERT ... ON CONFLICT (payment_id) DO NOTHING. Returns 1 if this call won
     * the slot, 0 if a refund already exists - the exactly-once guard under
     * concurrency (mirrors the webhook dedupe pattern). The row starts PENDING /
     * INITIATED; the winner completes it after the provider call.
     */
    @Modifying
    @Query(value = "INSERT INTO refunds(id, payment_id, amount_paise, status, sla_status, sla_due_at, trigger, created_at) "
            + "VALUES (:id, :paymentId, :amountPaise, 'PENDING', 'INITIATED', :slaDueAt, :trigger, now()) "
            + "ON CONFLICT (payment_id) DO NOTHING", nativeQuery = true)
    int insertInitiatedIfNew(@Param("id") UUID id,
                             @Param("paymentId") UUID paymentId,
                             @Param("amountPaise") long amountPaise,
                             @Param("slaDueAt") Date slaDueAt,
                             @Param("trigger") String trigger);

    /** Marks the provider outcome SUCCESS and links the funding float movement. */
    @Modifying
    @Query(value = "UPDATE refunds SET status = 'SUCCESS', provider_refund_id = :providerRefundId, "
            + "float_txn_id = :floatTxnId WHERE id = :id", nativeQuery = true)
    int completeInitiated(@Param("id") UUID id,
                          @Param("providerRefundId") String providerRefundId,
                          @Param("floatTxnId") UUID floatTxnId);

    /** INITIATED -> SETTLED, guarded so settlement is recorded at most once. */
    @Modifying
    @Query(value = "UPDATE refunds SET sla_status = 'SETTLED' WHERE payment_id = :paymentId AND sla_status = 'INITIATED'",
            nativeQuery = true)
    int markSettledIfInitiated(@Param("paymentId") UUID paymentId);

    /** INITIATED -> BREACHED, guarded so a breach is flagged (and its event emitted) once. */
    @Modifying
    @Query(value = "UPDATE refunds SET sla_status = 'BREACHED' WHERE id = :id AND sla_status = 'INITIATED'",
            nativeQuery = true)
    int markBreachedIfInitiated(@Param("id") UUID id);

    /** Open refunds already past their SLA deadline - candidates for a breach flag. */
    List<Refund> findBySlaStatusAndSlaDueAtBefore(String slaStatus, Date cutoff);
}
