package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.Payment;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends FirestoreReactiveRepository<Payment> {
}
