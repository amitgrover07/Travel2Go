package com.travel2go.backend.repository;

import com.travel2go.backend.model.SubscriptionCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionChargeRepository extends JpaRepository<SubscriptionCharge, UUID> {

    Optional<SubscriptionCharge> findByIdempotencyKey(String idempotencyKey);

    List<SubscriptionCharge> findBySubscriptionId(UUID subscriptionId);

    /**
     * Atomically claims a (subscription, period) billing slot via
     * INSERT ... ON CONFLICT (idempotency_key) DO NOTHING. Returns 1 if this call
     * is the first to bill this period, 0 if a renewal retry already did - so a
     * retried renewal never double-charges.
     */
    @Modifying
    @Query(value = "INSERT INTO subscription_charges(id, idempotency_key, subscription_id, amount_paise, status, created_at) "
            + "VALUES (:id, :idempotencyKey, :subscriptionId, :amountPaise, 'PENDING', now()) "
            + "ON CONFLICT (idempotency_key) DO NOTHING", nativeQuery = true)
    int insertIfNew(@Param("id") UUID id,
                    @Param("idempotencyKey") String idempotencyKey,
                    @Param("subscriptionId") UUID subscriptionId,
                    @Param("amountPaise") long amountPaise);

    @Modifying
    @Query(value = "UPDATE subscription_charges SET status = :status, provider_charge_id = :providerChargeId "
            + "WHERE idempotency_key = :idempotencyKey", nativeQuery = true)
    int complete(@Param("idempotencyKey") String idempotencyKey,
                 @Param("status") String status,
                 @Param("providerChargeId") String providerChargeId);
}
