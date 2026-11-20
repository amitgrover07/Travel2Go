package com.travel2go.backend.repository;

import com.travel2go.backend.model.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
    Optional<Approval> findByOrgIdAndBookingRef(UUID orgId, String bookingRef);
}
