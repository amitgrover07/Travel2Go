package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.TravelConfiguration;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TravelConfigurationRepository extends FirestoreReactiveRepository<TravelConfiguration> {
    Flux<TravelConfiguration> findByStatus(String status);
    Flux<TravelConfiguration> findByConfigCode(String configCode);
}
