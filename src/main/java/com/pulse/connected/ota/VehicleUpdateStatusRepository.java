package com.pulse.connected.ota;

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

    @Query("SELECT COUNT(u) FROM VehicleUpdateStatus u WHERE u.campaign.id = :campaignId AND u.status = :status")
    long countByCampaignIdAndStatus(@Param("campaignId") UUID campaignId, @Param("status") UpdateStatus status);

    @Query("SELECT COUNT(u) FROM VehicleUpdateStatus u WHERE u.campaign.id = :campaignId")
    long countTotalByCampaignId(@Param("campaignId") UUID campaignId);
}
