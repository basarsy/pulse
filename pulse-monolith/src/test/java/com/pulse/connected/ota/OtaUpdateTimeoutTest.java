package com.pulse.connected.ota;

import com.pulse.connected.AbstractIntegrationTest;
import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.domain.RolloutStageStatus;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.messaging.dto.OtaUpdateProgressEvent;
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
    void silentVehicles_TimeOut_AndDontCountAsFailures() {
        // One stage, 3 vehicles: one installed, one silent for 2 hours, one still reporting.
        // The threshold is low (10%): if timeouts counted as failures, the rollout would pause.
        Rollout rollout = rolloutWithUpdates(10.0, UpdateStatus.INSTALLED, UpdateStatus.DOWNLOADING, UpdateStatus.INSTALLING);
        UUID silent = rollout.updates().get(1);
        UUID active = rollout.updates().get(2);
        lastProgress(silent, Duration.ofHours(2));

        sweep.sweep();

        VehicleUpdateStatus timedOut = updateRepository.findById(silent).orElseThrow();
        assertThat(timedOut.getStatus()).isEqualTo(UpdateStatus.TIMED_OUT);
        assertThat(timedOut.getErrorMessage()).isEqualTo("No progress reported by the vehicle for 30 minutes");
        assertThat(timedOut.getCompletedAt()).isNotNull();
        assertThat(updateRepository.findById(active).orElseThrow().getStatus())
                .as("a vehicle that is still reporting keeps going").isEqualTo(UpdateStatus.INSTALLING);
        assertThat(campaignRepository.findById(rollout.campaignId()).orElseThrow().getStatus())
                .as("the stage waits for the vehicle that is still reporting")
                .isEqualTo(CampaignStatus.IN_PROGRESS);

        // The last vehicle goes silent too: the stage is finished, with no failures.
        lastProgress(active, Duration.ofHours(1));
        sweep.sweep();

        assertThat(updateRepository.findById(active).orElseThrow().getStatus()).isEqualTo(UpdateStatus.TIMED_OUT);
        RolloutCampaign campaign = campaignRepository.findById(rollout.campaignId()).orElseThrow();
        assertThat(campaign.getStages().get(0).getStatus()).isEqualTo(RolloutStageStatus.COMPLETED);
        assertThat(campaign.getStatus()).as("2 of 3 timed out, but none failed").isEqualTo(CampaignStatus.COMPLETED);
        CampaignProgress progress = CampaignProgress.of(updateRepository.countByStageAndStatus(rollout.campaignId()));
        assertThat(progress.timedOut()).isEqualTo(2);
        assertThat(progress.failed()).isZero();
    }

    /**
     * Timed-out vehicles are left out of the failure rate entirely: they don't dilute real failures
     * either. 1 failed of 4 is 25%, below the 40% threshold; leaving out the 2 timed out, it is 1 of 2.
     */
    @Test
    void realFailures_AreRatedAgainstVehiclesThatDidntTimeOut() {
        Rollout rollout = rolloutWithUpdates(40.0,
                UpdateStatus.INSTALLED, UpdateStatus.FAILED, UpdateStatus.DOWNLOADING, UpdateStatus.DOWNLOADING);
        lastProgress(rollout.updates().get(2), Duration.ofHours(2));
        lastProgress(rollout.updates().get(3), Duration.ofHours(2));

        sweep.sweep();

        assertThat(campaignRepository.findById(rollout.campaignId()).orElseThrow().getStatus())
                .as("1 failed of the 2 that reported is 50%, above the 40% threshold")
                .isEqualTo(CampaignStatus.PAUSED);
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

        assertThat(campaignService.timeOutIfStalled(update, cutoff, "timed out")).isFalse();
        assertThat(updateRepository.findById(update).orElseThrow().getStatus()).isEqualTo(UpdateStatus.DOWNLOADING);
    }

    /**
     * A parked car resumes after its update timed out and installs it: the outcome it reports
     * replaces the timeout, and the vehicle's software version shows what it runs now.
     */
    @Test
    void installAfterTimeout_ReplacesTheTimeout() {
        Rollout rollout = rolloutWithUpdates(50.0, UpdateStatus.INSTALLED, UpdateStatus.DOWNLOADING);
        UUID update = rollout.updates().get(1);
        lastProgress(update, Duration.ofHours(2));
        sweep.sweep();
        assertThat(updateRepository.findById(update).orElseThrow().getStatus()).isEqualTo(UpdateStatus.TIMED_OUT);

        UUID vehicleId = jdbc.queryForObject("SELECT vehicle_id FROM vehicle_update_status WHERE id = ?", UUID.class, update);
        String target = jdbc.queryForObject("""
                SELECT s.version_label FROM vehicle_update_status u JOIN software_versions s ON s.id = u.target_version_id
                WHERE u.id = ?""", String.class, update);
        // Intermediate progress after the timeout is ignored; only an outcome settles it.
        campaignService.handleVehicleUpdateProgress(new OtaUpdateProgressEvent(
                update, vehicleId, rollout.campaignId(), UpdateStatus.INSTALLING, 80, null, null));
        assertThat(updateRepository.findById(update).orElseThrow().getStatus()).isEqualTo(UpdateStatus.TIMED_OUT);
        campaignService.handleVehicleUpdateProgress(new OtaUpdateProgressEvent(
                update, vehicleId, rollout.campaignId(), UpdateStatus.INSTALLED, 100, null, null));

        VehicleUpdateStatus installed = updateRepository.findById(update).orElseThrow();
        assertThat(installed.getStatus()).isEqualTo(UpdateStatus.INSTALLED);
        assertThat(installed.getErrorMessage()).isNull();
        assertThat(vehicleRepository.findById(vehicleId).orElseThrow().getCurrentSoftwareVersion()).isEqualTo(target);
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
