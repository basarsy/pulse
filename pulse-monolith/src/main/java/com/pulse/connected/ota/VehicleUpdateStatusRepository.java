package com.pulse.connected.ota;

import com.pulse.connected.domain.UpdateStatus;

import com.pulse.connected.vehicle.Vehicle;
import org.springframework.data.domain.Pageable;
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

    // --- Rollout targeting: stages are cumulative shares of a model's fleet, in VIN order. ---

    /** Size of the model's fleet (model matched case-insensitively). */
    @Query("SELECT COUNT(v) FROM Vehicle v WHERE UPPER(v.model) = UPPER(:model)")
    long countVehiclesOfModel(@Param("model") String model);

    /** The model's VINs in order; with {@code PageRequest.of(n - 1, 1)}, the VIN that ends a share of n. */
    @Query("SELECT v.vin FROM Vehicle v WHERE UPPER(v.model) = UPPER(:model) ORDER BY v.vin")
    List<String> findVinsOfModel(@Param("model") String model, Pageable page);

    /**
     * Vehicles of the model up to {@code lastVin} (in VIN order) that still need instructing:
     * not targeted in this campaign yet and not already on the target version.
     */
    @Query("""
            SELECT v FROM Vehicle v
            WHERE UPPER(v.model) = UPPER(:model)
              AND v.vin <= :lastVin
              AND v.currentSoftwareVersion <> :targetVersionLabel
              AND NOT EXISTS (SELECT 1 FROM VehicleUpdateStatus u WHERE u.campaign.id = :campaignId AND u.vehicle = v)
            ORDER BY v.vin
            """)
    List<Vehicle> findVehiclesToInstruct(@Param("campaignId") UUID campaignId,
                                         @Param("model") String model,
                                         @Param("lastVin") String lastVin,
                                         @Param("targetVersionLabel") String targetVersionLabel);
}
