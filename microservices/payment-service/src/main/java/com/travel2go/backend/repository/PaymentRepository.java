package com.travel2go.backend.repository;

import com.travel2go.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingRef(String bookingRef);

    Optional<Payment> findByProviderRef(String providerRef);

    /**
     * Atomically transitions a payment CREATED -> CAPTURED. The WHERE clause
     * is the race guard: only one concurrent caller's UPDATE can match a row
     * still in CREATED, so at most one of them ever sees a return value > 0.
     */
    @Modifying
    @Query("UPDATE Payment p SET p.status = 'CAPTURED', p.providerPaymentId = :providerPaymentId, "
            + "p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id AND p.status = 'CREATED'")
    int markCaptured(@Param("id") UUID id, @Param("providerPaymentId") String providerPaymentId);
}
