package com.travel2go.backend.repository;

import com.travel2go.backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBookingRef(String bookingRef);

    Optional<Payment> findByProviderRef(String providerRef);
}
