package com.travel2go.backend.repository;

import com.travel2go.backend.model.VerificationCode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationCodeRepository extends MongoRepository<VerificationCode, String> {
    Optional<VerificationCode> findByEmailAndCode(String email, String code);
    void deleteByEmail(String email);
}
