package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.VerificationCode;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface VerificationCodeRepository extends FirestoreReactiveRepository<VerificationCode> {
    Mono<VerificationCode> findByEmailAndCode(String email, String code);
    Mono<Void> deleteByEmail(String email);
    Mono<VerificationCode> findByPhoneAndCode(String phone, String code);
    Mono<Void> deleteByPhone(String phone);
}
