package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.Payment;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PaymentRepository extends FirestoreReactiveRepository<Payment> {
    Flux<Payment> findByBookingRef(String bookingRef);

    Flux<Payment> findByProviderRef(String providerRef);
}
