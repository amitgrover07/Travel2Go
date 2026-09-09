package com.travel2go.backend.repository;

import com.travel2go.backend.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByPaymentId(UUID paymentId);

    @Query("SELECT COALESCE(SUM(r.amountPaise), 0) FROM Refund r WHERE r.status = 'SUCCESS'")
    long sumRefundedAmountPaise();
}
