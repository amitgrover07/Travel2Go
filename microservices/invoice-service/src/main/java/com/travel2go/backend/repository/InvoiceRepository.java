package com.travel2go.backend.repository;

import com.travel2go.backend.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByBookingRef(String bookingRef);

    Optional<Invoice> findByOrgIdAndPeriod(UUID orgId, String period);
}
