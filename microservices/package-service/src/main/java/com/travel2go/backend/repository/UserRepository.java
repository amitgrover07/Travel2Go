package com.travel2go.backend.repository;

import com.travel2go.backend.model.User;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends FirestoreReactiveRepository<User> {
    Mono<User> findByEmail(String email);
    Mono<User> findByPhone(String phone);
}
