package com.travel2go.backend.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.travel2go.backend.model.AllocationRule;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AllocationRuleRepository extends FirestoreReactiveRepository<AllocationRule> {
    Flux<AllocationRule> findByStatus(String status);
    Flux<AllocationRule> findByRuleCode(String ruleCode);
}
