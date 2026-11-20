package com.travel2go.backend.repository;

import com.travel2go.backend.model.OrgMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrgMemberRepository extends JpaRepository<OrgMember, UUID> {
    Optional<OrgMember> findByOrgIdAndUserId(UUID orgId, String userId);

    List<OrgMember> findByOrgId(UUID orgId);
}
