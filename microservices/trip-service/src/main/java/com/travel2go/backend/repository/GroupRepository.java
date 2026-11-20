package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.TravellerGroup;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface GroupRepository extends FirestoreReactiveRepository<TravellerGroup> {
    Mono<TravellerGroup> findByInviteToken(String inviteToken);
}
