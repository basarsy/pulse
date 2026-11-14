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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rollout queries against real Postgres. Each test runs in a transaction that is rolled
 * back, and uses its own model name, so only its own rows ever match.
 */
@Transactional
class RolloutQueriesTest extends AbstractIntegrationTest {

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

    private User owner;
    private SoftwareVersion version;
    private String model;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .email(uniqueEmail("rollout"))
                .passwordHash("not-a-real-hash")
                .fullName("Rollout Test")
                .roles(Set.of(Role.FLEET_ADMIN))
                .build());
        version = softwareVersionRepository.save(SoftwareVersion.builder()
                .versionLabel("v-" + UUID.randomUUID().toString().substring(0, 20))
                .packageUrl("https://ota.example/pkg.bin")
                .checksum("0".repeat(64))
                .mandatory(false)
                .build());
        model = "Model-" + UUID.randomUUID();
    }

    @Test
    void summary_GroupsByStageAndStatus() {
        RolloutCampaign campaign = saveCampaign();
        saveUpdate(campaign, saveVehicle("A"), 0, UpdateStatus.INSTALLED);
        saveUpdate(campaign, saveVehicle("B"), 0, UpdateStatus.FAILED);
        saveUpdate(campaign, saveVehicle("C"), 1, UpdateStatus.INSTALLED);

        CampaignProgress progress = CampaignProgress.of(updateRepository.countByStageAndStatus(campaign.getId()));

        assertThat(progress.stageTotal(0)).isEqualTo(2);
        assertThat(progress.stageFailed(0)).isEqualTo(1);
        assertThat(progress.stageTotal(1)).isEqualTo(1);
        assertThat(progress.installed()).isEqualTo(2);
    }

    @Test
    void vehiclesToInstruct_FirstNByVin() {
        RolloutCampaign campaign = saveCampaign();
        Vehicle a = saveVehicle("A");
        Vehicle b = saveVehicle("B");
        saveVehicle("C");
        saveVehicle("D");

        // Stage share of 2: the cutoff is the 2nd VIN in order.
        String lastVin = updateRepository.findVinsOfModel(model, PageRequest.of(1, 1)).get(0);
        assertThat(lastVin).isEqualTo(b.getVin());

        assertThat(updateRepository.findVehiclesToInstruct(campaign.getId(), model, lastVin, version.getVersionLabel()))
                .extracting(Vehicle::getId)
                .containsExactly(a.getId(), b.getId());
    }

    @Test
    void vehiclesToInstruct_SkipsAlreadyTargetedAndOnTargetVersion() {
        RolloutCampaign campaign = saveCampaign();
        Vehicle a = saveVehicle("A");
        saveVehicle("B", model, version.getVersionLabel()); // already on the target version
        Vehicle c = saveVehicle("C");
        Vehicle d = saveVehicle("D");
        saveUpdate(campaign, a, 0, UpdateStatus.INSTALLED); // targeted in an earlier stage

        assertThat(updateRepository.findVehiclesToInstruct(campaign.getId(), model, d.getVin(), version.getVersionLabel()))
                .extracting(Vehicle::getId)
                .containsExactly(c.getId(), d.getId());
    }

    @Test
    void vehiclesOfModel_MatchCaseInsensitively() {
        saveVehicle("A", model.toLowerCase(), "1.0.0");
        saveVehicle("B", model.toLowerCase(), "1.0.0");

        assertThat(updateRepository.countVehiclesOfModel(model.toUpperCase())).isEqualTo(2);
        assertThat(updateRepository.findVinsOfModel(model.toUpperCase(), PageRequest.of(0, 10))).hasSize(2);
    }

    RolloutCampaign saveCampaign() {
        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version)
                .targetModel(model)
                .status(CampaignStatus.IN_PROGRESS)
                .currentStageIndex(0)
                .failureThresholdPercent(10.0)
                .createdBy(owner)
                .build();
        List<RolloutStage> stages = new ArrayList<>();
        for (int pct : new int[]{50, 100}) {
            stages.add(RolloutStage.builder().campaign(campaign).percentage(pct).status(RolloutStageStatus.PENDING).build());
        }
        campaign.setStages(stages);
        return campaignRepository.saveAndFlush(campaign);
    }

    /** VINs are prefixed so tests can control their order; the suffix keeps them unique. */
    Vehicle saveVehicle(String vinPrefix) {
        return saveVehicle(vinPrefix, model, "1.0.0");
    }

    Vehicle saveVehicle(String vinPrefix, String vehicleModel, String softwareVersion) {
        String vin = (vinPrefix + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
        return vehicleRepository.saveAndFlush(Vehicle.builder()
                .vin(vin)
                .model(vehicleModel)
                .modelYear(2026)
                .owner(owner)
                .connectivityState(ConnectivityState.OFFLINE)
                .currentSoftwareVersion(softwareVersion)
                .build());
    }

    VehicleUpdateStatus saveUpdate(RolloutCampaign campaign, Vehicle vehicle, int stageIndex, UpdateStatus status) {
        return updateRepository.saveAndFlush(VehicleUpdateStatus.builder()
                .vehicle(vehicle)
                .campaign(campaign)
                .targetVersion(version)
                .stageIndex(stageIndex)
                .status(status)
                .progressPercent(0)
                .build());
    }
}
