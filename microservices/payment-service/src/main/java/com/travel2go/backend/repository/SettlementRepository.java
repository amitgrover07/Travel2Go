package com.travel2go.backend.repository;

import com.travel2go.backend.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    List<Settlement> findByPaymentId(UUID paymentId);

    @Query("SELECT COALESCE(SUM(s.amountPaise), 0) FROM Settlement s")
    long sumSettledAmountPaise();
}
