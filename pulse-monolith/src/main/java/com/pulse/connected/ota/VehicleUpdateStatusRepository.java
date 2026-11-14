package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleUpdateStatusRepository extends JpaRepository<VehicleUpdateStatus, UUID> {

    List<VehicleUpdateStatus> findAllByCampaignId(UUID campaignId);

    List<VehicleUpdateStatus> findAllByVehicleIdOrderByStartedAtDesc(UUID vehicleId);

    Optional<VehicleUpdateStatus> findByVehicleIdAndCampaignId(UUID vehicleId, UUID campaignId);



    /**
     * One row per (stage, status) with its number of updates: everything stage evaluation and the
     * campaign response need, in one grouped query instead of loading every update.
     */
    @Query("""
            SELECT new com.pulse.connected.ota.StageStatusCount(u.stageIndex, u.status, COUNT(u))
            FROM VehicleUpdateStatus u
            WHERE u.campaign.id = :campaignId
            GROUP BY u.stageIndex, u.status
            """)
    List<StageStatusCount> countByStageAndStatus(@Param("campaignId") UUID campaignId);
}
