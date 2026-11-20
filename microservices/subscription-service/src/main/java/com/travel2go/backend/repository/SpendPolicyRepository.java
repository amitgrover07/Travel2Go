package com.travel2go.backend.repository;

import com.travel2go.backend.model.SpendPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpendPolicyRepository extends JpaRepository<SpendPolicy, UUID> {
}
