package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.HolidayPackage;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface HolidayPackageRepository extends FirestoreReactiveRepository<HolidayPackage> {
    Flux<HolidayPackage> findByStatus(String status);
}
