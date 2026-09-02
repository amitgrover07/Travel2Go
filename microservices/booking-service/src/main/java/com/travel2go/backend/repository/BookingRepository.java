package com.travel2go.backend.repository;

import com.travel2go.backend.model.Booking;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface BookingRepository extends FirestoreReactiveRepository<Booking> {
    Flux<Booking> findByLegId(String legId);
}
