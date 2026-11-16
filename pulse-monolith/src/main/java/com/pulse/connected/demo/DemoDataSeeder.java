package com.pulse.connected.demo;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.domain.ConnectivityState;
import com.pulse.connected.domain.RolloutStageStatus;
import com.pulse.connected.domain.UpdateStatus;
import com.pulse.connected.ota.RolloutCampaign;
import com.pulse.connected.ota.RolloutCampaignRepository;
import com.pulse.connected.ota.RolloutStage;
import com.pulse.connected.ota.SoftwareVersion;
import com.pulse.connected.ota.SoftwareVersionRepository;
import com.pulse.connected.ota.VehicleUpdateStatus;
import com.pulse.connected.ota.VehicleUpdateStatusRepository;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds a demo fleet on startup so a local stack opens onto real data. Only active with
 * {@code pulse.demo.enabled=true} (Docker Compose sets {@code DEMO_ENABLED}); the demo logins have
 * published passwords, so it must never be enabled anywhere else.
 *
 * <p>Creates a fleet admin and an owner, 30 vehicles across three made-up models, versions
 * v1.0.0 (installed fleet-wide), v1.1.0 (already rolled out to Pulse Vela by a completed campaign)
 * and v1.2.0 (ready to roll out). Writes go through the entities, so the completed campaign looks
 * exactly as if the app had run it. Does nothing if the demo admin already exists.
 */
@Component
@ConditionalOnProperty(name = "pulse.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String ADMIN_EMAIL = "admin@pulse.demo";
    static final String OWNER_EMAIL = "owner@pulse.demo";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String BASELINE = "v1.0.0";
    private static final String ROLLED_OUT = "v1.1.0";
    private static final String READY = "v1.2.0";
    private static final String ROLLED_OUT_MODEL = "Pulse Vela";
    private static final List<Model> FLEET = List.of(
            new Model("Pulse Aurora", 'A', 12, 2024),
            new Model("Pulse Vela", 'V', 10, 2023),
            new Model("Pulse Terra", 'T', 8, 2025));
    private static final int OWNER_VEHICLES = 3;

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final SoftwareVersionRepository versions;
    private final RolloutCampaignRepository campaigns;
    private final VehicleUpdateStatusRepository updates;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;

    public DemoDataSeeder(UserRepository users, VehicleRepository vehicles, SoftwareVersionRepository versions,
                          RolloutCampaignRepository campaigns, VehicleUpdateStatusRepository updates,
                          PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.vehicles = vehicles;
        this.versions = versions;
        this.campaigns = campaigns;
        this.updates = updates;
        this.passwordEncoder = passwordEncoder;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Creates the demo data in one transaction, unless the demo admin already exists. */
    public void seed() {
        tx.executeWithoutResult(status -> {
            if (users.existsByEmail(ADMIN_EMAIL)) {
                log.info("Demo data already present; sign in as {} / demo-admin or {} / demo-owner", ADMIN_EMAIL, OWNER_EMAIL);
                return;
            }
            User admin = user(ADMIN_EMAIL, "demo-admin", "Fleet Admin (demo)", Role.FLEET_ADMIN);
            User owner = user(OWNER_EMAIL, "demo-owner", "Olivia Owner (demo)", Role.OWNER);

            SoftwareVersion rolledOut = version(ROLLED_OUT, "Faster charging curve and a calmer climate fan.");
            version(BASELINE, "Factory release.");
            version(READY, "Remote start pre-conditions the battery; navigation map refresh.");

            List<Vehicle> rolledOutModel = new ArrayList<>();
            for (Model model : FLEET) {
                for (int i = 1; i <= model.count(); i++) {
                    boolean ownersCar = model.name().equals(ROLLED_OUT_MODEL) && i <= OWNER_VEHICLES;
                    boolean updated = model.name().equals(ROLLED_OUT_MODEL);
                    Vehicle vehicle = vehicles.save(Vehicle.builder()
                            .vin(String.format("PLS%cDEMO%09d", model.code(), i))
                            .model(model.name())
                            .modelYear(model.year())
                            .owner(ownersCar ? owner : admin)
                            .authorizedUsers(new HashSet<>())
                            .connectivityState(ConnectivityState.OFFLINE)
                            .currentSoftwareVersion(updated ? ROLLED_OUT : BASELINE)
                            // Varied but repeatable readings; the simulator carries on from these.
                            .batteryOrFuelLevel(35.0 + (i * 37 + model.code()) % 60)
                            .odometerKm(1_500.0 + ((i * 7_919 + model.code() * 31) % 42_000))
                            .build());
                    if (updated) {
                        rolledOutModel.add(vehicle);
                    }
                }
            }

            completedRollout(rolledOut, rolledOutModel, admin);
            log.info("Seeded the demo fleet; sign in as {} / demo-admin or {} / demo-owner", ADMIN_EMAIL, OWNER_EMAIL);
        });
    }

    private User user(String email, String password, String fullName, Role role) {
        return users.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .roles(new HashSet<>(Set.of(role)))
                .build());
    }

    private SoftwareVersion version(String label, String notes) {
        return versions.save(SoftwareVersion.builder()
                .versionLabel(label)
                .releaseNotes(notes)
                .checksum("sha256:demo-" + label)
                .packageUrl("https://ota.pulse.demo/packages/" + label + ".bin")
                .mandatory(false)
                .build());
    }

    /**
     * A finished 50% → 100% rollout of {@code version} to the given model, split the way the service
     * splits a fleet (by VIN order), with every update installed.
     */
    private void completedRollout(SoftwareVersion version, List<Vehicle> fleet, User admin) {
        // The campaign's creation time is set to now on save, so its history follows right after.
        Instant start = Instant.now();
        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version)
                .targetModel(ROLLED_OUT_MODEL)
                .status(CampaignStatus.COMPLETED)
                .currentStageIndex(1)
                .failureThresholdPercent(10.0)
                .createdBy(admin)
                .build();
        List<Integer> percentages = List.of(50, 100);
        List<RolloutStage> stages = new ArrayList<>();
        for (int s = 0; s < percentages.size(); s++) {
            stages.add(RolloutStage.builder()
                    .campaign(campaign)
                    .percentage(percentages.get(s))
                    .status(RolloutStageStatus.COMPLETED)
                    .startedAt(start.plus(Duration.ofSeconds(30L * s)))
                    .completedAt(start.plus(Duration.ofSeconds(30L * s + 25)))
                    .build());
        }
        campaign.setStages(stages);
        RolloutCampaign saved = campaigns.save(campaign);

        List<Vehicle> byVin = fleet.stream().sorted(Comparator.comparing(Vehicle::getVin)).toList();
        int firstStageSize = (int) Math.ceil(byVin.size() * percentages.get(0) / 100.0);
        for (int i = 0; i < byVin.size(); i++) {
            int stage = i < firstStageSize ? 0 : 1;
            Instant updateStart = start.plus(Duration.ofSeconds(30L * stage + i));
            updates.save(VehicleUpdateStatus.builder()
                    .vehicle(byVin.get(i))
                    .campaign(saved)
                    .targetVersion(version)
                    .stageIndex(stage)
                    .status(UpdateStatus.INSTALLED)
                    .progressPercent(100)
                    .startedAt(updateStart)
                    .completedAt(updateStart.plus(Duration.ofSeconds(8)))
                    .build());
        }
    }

    private record Model(String name, char code, int count, int year) {
    }
}
