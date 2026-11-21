package com.pulse.connected.ota;

import com.pulse.connected.AbstractIntegrationTest;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.domain.RolloutStageStatus;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A vehicle that stops reporting must not hold its rollout stage open forever: after the update
 * timeout (30 minutes by default) its update fails, and the stage is evaluated as for any failure.
 */
class OtaUpdateTimeoutTest extends AbstractIntegrationTest {

    @Autowired
    private OtaUpdateTimeoutSweep sweep;

    @Autowired
    private RolloutCampaignService campaignService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private SoftwareVersionRepository softwareVersionRepository;

    @Autowired
    private RolloutCampaignRepository campaignRepository;

    @Autowired
    private VehicleUpdateStatusRepository updateRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void silentVehicle_FailsAfterTimeout_AndStageCompletes() {
        // One stage, 3 vehicles: one installed, one silent for 2 hours, one still reporting.
        Rollout rollout = rolloutWithUpdates(50.0, UpdateStatus.INSTALLED, UpdateStatus.DOWNLOADING, UpdateStatus.INSTALLING);
        UUID silent = rollout.updates().get(1);
        UUID active = rollout.updates().get(2);
        lastProgress(silent, Duration.ofHours(2));

        sweep.sweep();

        VehicleUpdateStatus failed = updateRepository.findById(silent).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(UpdateStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("No progress reported by the vehicle for 30 minutes");
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(updateRepository.findById(active).orElseThrow().getStatus())
                .as("a vehicle that is still reporting keeps going").isEqualTo(UpdateStatus.INSTALLING);
        assertThat(campaignRepository.findById(rollout.campaignId()).orElseThrow().getStatus())
                .as("1 of 3 failed is within the 50% threshold, and the stage isn't finished")
                .isEqualTo(CampaignStatus.IN_PROGRESS);

        // The last vehicle goes silent too: the stage is finished, with 2 of 3 failed (> 50%).
        lastProgress(active, Duration.ofHours(1));
        sweep.sweep();

        assertThat(updateRepository.findById(active).orElseThrow().getStatus()).isEqualTo(UpdateStatus.FAILED);
        assertThat(campaignRepository.findById(rollout.campaignId()).orElseThrow().getStatus())
                .as("too many silent vehicles pause the rollout, like failed installs")
                .isEqualTo(CampaignStatus.PAUSED);
    }

    @Test
    void silentVehicles_WithinThreshold_CompleteTheRollout() {
        Rollout rollout = rolloutWithUpdates(50.0, UpdateStatus.INSTALLED, UpdateStatus.PENDING);
        lastProgress(rollout.updates().get(1), Duration.ofHours(2));

        sweep.sweep();

        assertThat(updateRepository.findById(rollout.updates().get(1)).orElseThrow().getStatus())
                .isEqualTo(UpdateStatus.FAILED);
        RolloutCampaign campaign = campaignRepository.findById(rollout.campaignId()).orElseThrow();
        assertThat(campaign.getStages().get(0).getStatus()).isEqualTo(RolloutStageStatus.COMPLETED);
        assertThat(campaign.getStatus()).as("no longer stuck IN_PROGRESS").isEqualTo(CampaignStatus.COMPLETED);
    }

    /** Progress can arrive between the sweep's query and the lock: the update then keeps running. */
    @Test
    void progressAfterTheSweepPickedTheUpdate_KeepsItRunning() {
        Rollout rollout = rolloutWithUpdates(50.0, UpdateStatus.DOWNLOADING);
        UUID update = rollout.updates().get(0);
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(30));
        // It was stalled when the sweep looked ...
        lastProgress(update, Duration.ofHours(2));
        assertThat(updateRepository.findStalledUpdateIds(cutoff)).contains(update);
        // ... and then the vehicle reported.
        lastProgress(update, Duration.ZERO);

        assertThat(campaignService.failIfStalled(update, cutoff, "timed out")).isFalse();
        assertThat(updateRepository.findById(update).orElseThrow().getStatus()).isEqualTo(UpdateStatus.DOWNLOADING);
    }

    private void lastProgress(UUID updateId, Duration ago) {
        jdbc.update("UPDATE vehicle_update_status SET last_progress_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(ago)), updateId);
    }

    private record Rollout(UUID campaignId, List<UUID> updates) {
    }

    /**
     * A rollout written directly in its first (and only, 100%) stage, with one vehicle update per
     * status. No instructions are published, so the simulator never reports on these updates.
     */
    private Rollout rolloutWithUpdates(double failureThreshold, UpdateStatus... statuses) {
        User admin = userRepository.save(User.builder()
                .email(uniqueEmail("ota-timeout"))
                .passwordHash("not-a-real-hash")
                .fullName("OTA Timeout")
                .roles(Set.of(Role.FLEET_ADMIN))
                .build());
        SoftwareVersion version = softwareVersionRepository.save(SoftwareVersion.builder()
                .versionLabel("timeout-" + UUID.randomUUID().toString().substring(0, 20))
                .packageUrl("https://ota.example/pkg.bin")
                .checksum("0".repeat(64))
                .mandatory(false)
                .build());
        String model = "TimeoutModel-" + UUID.randomUUID();

        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version).targetModel(model)
                .status(CampaignStatus.IN_PROGRESS).currentStageIndex(0)
                .failureThresholdPercent(failureThreshold).createdBy(admin)
                .build();
        campaign.setStages(new ArrayList<>(List.of(
                RolloutStage.builder().campaign(campaign).percentage(100).status(RolloutStageStatus.IN_PROGRESS).build())));
        UUID campaignId = campaignRepository.save(campaign).getId();

        List<UUID> updates = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            String vin = (String.format("T%02d", i) + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
            Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                    .vin(vin).model(model).modelYear(2026).owner(admin)
                    .connectivityState(ConnectivityState.OFFLINE)
                    .currentSoftwareVersion(statuses[i] == UpdateStatus.INSTALLED ? version.getVersionLabel() : "1.0.0")
                    .build());
            updates.add(updateRepository.save(VehicleUpdateStatus.builder()
                    .vehicle(vehicle).campaign(campaign).targetVersion(version)
                    .stageIndex(0).status(statuses[i]).progressPercent(statuses[i] == UpdateStatus.INSTALLED ? 100 : 40)
                    .build()).getId());
        }
        return new Rollout(campaignId, updates);
    }
}
