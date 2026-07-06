package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.Leg;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface LegRepository extends FirestoreReactiveRepository<Leg> {
    Flux<Leg> findByTripId(String tripId);
}
