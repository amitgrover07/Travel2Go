package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.WaitlistCheck;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface WaitlistCheckRepository extends FirestoreReactiveRepository<WaitlistCheck> {
    Mono<WaitlistCheck> findByShareToken(String shareToken);
}
