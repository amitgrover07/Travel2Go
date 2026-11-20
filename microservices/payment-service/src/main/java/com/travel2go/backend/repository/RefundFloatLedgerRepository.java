package com.travel2go.backend.repository;

import com.travel2go.backend.model.RefundFloatLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefundFloatLedgerRepository extends JpaRepository<RefundFloatLedger, UUID> {

    /**
     * The authoritative float balance: signed sum over the ledger (CREDIT +, DEBIT -).
     * Always correct regardless of any interleaving on the balance_after snapshot column.
     */
    @Query(value = "SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount_paise ELSE -amount_paise END), 0) "
            + "FROM refund_float_ledger", nativeQuery = true)
    long currentBalancePaise();
}
