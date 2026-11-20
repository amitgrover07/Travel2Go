package com.travel2go.backend.repository;

import com.travel2go.backend.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByUserId(String userId);

    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(String userId);
}
