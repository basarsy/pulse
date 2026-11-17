package com.pulse.connected.ota;

import com.pulse.connected.domain.CampaignStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RolloutCampaignRepository extends JpaRepository<RolloutCampaign, UUID> {
    List<RolloutCampaign> findAllByTargetModel(String targetModel);
    List<RolloutCampaign> findAllByStatus(CampaignStatus status);

    List<RolloutCampaign> findAllByOrderByCreatedAtDesc();

    long countByStatus(CampaignStatus status);

    /**
     * Loads the campaign with a row lock ({@code SELECT ... FOR UPDATE}) held until the transaction
     * ends. Progress events and operator actions for one campaign take it first, so they are applied
     * one after another and each sees the previous one's committed changes; different campaigns
     * still run in parallel. No fetch join: Postgres refuses FOR UPDATE across an outer join, and the
     * stages load in their own select.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM RolloutCampaign c WHERE c.id = :id")
    Optional<RolloutCampaign> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Raises the campaign's version by one, in the database only: the caller refreshes the entity
     * afterwards. Not cleared automatically, so the persistence context (and its other entities)
     * stay attached.
     */
    @Modifying
    @Query("UPDATE RolloutCampaign c SET c.version = c.version + 1 WHERE c.id = :id")
    int incrementVersion(@Param("id") UUID id);
}
