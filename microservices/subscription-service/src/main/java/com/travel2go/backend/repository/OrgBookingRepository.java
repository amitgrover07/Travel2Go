package com.travel2go.backend.repository;

import com.travel2go.backend.model.OrgBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrgBookingRepository extends JpaRepository<OrgBooking, UUID> {

    List<OrgBooking> findByOrgIdAndPeriod(UUID orgId, String period);

    Optional<OrgBooking> findByOrgIdAndBookingRef(UUID orgId, String bookingRef);

    @Query("SELECT COALESCE(SUM(b.amountPaise), 0) FROM OrgBooking b WHERE b.orgId = :orgId AND b.period = :period")
    long sumForPeriod(@Param("orgId") UUID orgId, @Param("period") String period);
}
