package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.CustomPackage;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface CustomPackageRepository extends FirestoreReactiveRepository<CustomPackage> {
    Flux<CustomPackage> findByStatus(String status);
    Flux<CustomPackage> findByPackageCode(String packageCode);
}
