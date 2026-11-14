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
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The last progress events of a stage arriving in parallel must advance the stage exactly once:
 * not zero times (each transaction missing the other's update → the rollout stalls) and not twice
 * (both starting the next stage → duplicate updates). Not @Transactional: the events have to
 * commit and race for real.
 */
class RolloutConcurrencyTest extends AbstractIntegrationTest {

    private static final int FLEET = 10;
    private static final int STAGE_ONE = 5; // 50% of 10

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
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Test
    void parallelFinalEvents_AdvanceStageExactlyOnce() throws Exception {
        User admin = userRepository.save(User.builder()
                .email(uniqueEmail("rollout-race"))
                .passwordHash("not-a-real-hash")
                .fullName("Rollout Race")
                .roles(Set.of(Role.FLEET_ADMIN))
                .build());
        SoftwareVersion version = softwareVersionRepository.save(SoftwareVersion.builder()
                .versionLabel("race-" + UUID.randomUUID().toString().substring(0, 20))
                .packageUrl("https://ota.example/pkg.bin")
                .checksum("0".repeat(64))
                .mandatory(false)
                .build());
        String model = "RaceModel-" + UUID.randomUUID();
        List<Vehicle> fleet = new ArrayList<>();
        for (int i = 0; i < FLEET; i++) {
            // Two-digit prefix fixes the VIN order; the random rest keeps VINs unique.
            String vin = (String.format("R%02d", i) + UUID.randomUUID().toString().replace("-", "")).substring(0, 17).toUpperCase();
            fleet.add(vehicleRepository.save(Vehicle.builder()
                    .vin(vin).model(model).modelYear(2026).owner(admin)
                    .connectivityState(ConnectivityState.OFFLINE).currentSoftwareVersion("1.0.0")
                    .build()));
        }

        // Mid-rollout state, written directly: stage 1 (50%) in progress, its 5 updates installing.
        // No instructions were published for these, so the simulator never touches them.
        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version).targetModel(model)
                .status(CampaignStatus.IN_PROGRESS).currentStageIndex(0)
                .failureThresholdPercent(10.0).createdBy(admin)
                .build();
        campaign.setStages(new ArrayList<>(List.of(
                RolloutStage.builder().campaign(campaign).percentage(50).status(RolloutStageStatus.IN_PROGRESS).build(),
                RolloutStage.builder().campaign(campaign).percentage(100).status(RolloutStageStatus.PENDING).build())));
        UUID campaignId = campaignRepository.save(campaign).getId();
        List<VehicleUpdateStatus> stageOne = new ArrayList<>();
        for (int i = 0; i < STAGE_ONE; i++) {
            stageOne.add(updateRepository.save(VehicleUpdateStatus.builder()
                    .vehicle(fleet.get(i)).campaign(campaign).targetVersion(version)
                    .stageIndex(0).status(UpdateStatus.INSTALLING).progressPercent(90)
                    .build()));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(STAGE_ONE);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (VehicleUpdateStatus update : stageOne) {
                OtaUpdateProgressEvent installed = OtaUpdateProgressEvent.builder()
                        .updateStatusId(update.getId())
                        .vehicleId(update.getVehicle().getId())
                        .campaignId(campaignId)
                        .status(UpdateStatus.INSTALLED)
                        .progressPercent(100)
                        .build();
                results.add(pool.submit(() -> {
                    start.await();
                    campaignService.handleVehicleUpdateProgress(installed);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS); // rethrows any event's failure (e.g. a duplicate stage start)
            }
        } finally {
            pool.shutdownNow();
        }

        RolloutCampaign after = campaignRepository.findById(campaignId).orElseThrow();
        assertThat(after.getStages().get(0).getStatus())
                .as("stage 1 completed (not stalled)").isEqualTo(RolloutStageStatus.COMPLETED);
        assertThat(after.getCurrentStageIndex()).isEqualTo(1);

        CampaignProgress progress = CampaignProgress.of(updateRepository.countByStageAndStatus(campaignId));
        assertThat(progress.stageTotal(0)).isEqualTo(STAGE_ONE);
        assertThat(progress.stageTotal(1)).as("stage 2 started exactly once").isEqualTo(FLEET - STAGE_ONE);
        assertThat(progress.total()).isEqualTo(FLEET);
    }

    @Test
    void otaProgressListener_RunsWithThreeThreads() {
        ConcurrentMessageListenerContainer<?, ?> container =
                (ConcurrentMessageListenerContainer<?, ?>) listenerRegistry.getListenerContainer("ota-progress");

        assertThat(container).isNotNull();
        assertThat(container.getConcurrency()).isEqualTo(3);
    }
}
