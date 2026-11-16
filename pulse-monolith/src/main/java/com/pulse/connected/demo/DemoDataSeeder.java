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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.UUID;

/**
 * Seeds a demo fleet on startup so a local stack opens onto real data. Only active with
 * {@code pulse.demo.enabled=true} (Docker Compose sets {@code DEMO_ENABLED}); the demo logins have
 * published passwords, so it must never be enabled anywhere else. Meant for a single instance.
 *
 * <p>Creates a fleet admin and an owner, 30 vehicles across three made-up models, versions
 * v1.0.0 (installed fleet-wide), v1.1.0 (already rolled out to Pulse Vela by a completed campaign)
 * and v1.2.0 (ready to roll out). Rows are written through the entities; afterwards one SQL step
 * moves their creation times a week back, because {@code @CreationTimestamp} always stamps "now"
 * and the rollout history has to come after creation and before the present. No audit entries are
 * written for the seeded campaign.
 *
 * <p>Seeds only into a database that has none of the demo users, VINs or versions, and never stops
 * the application from starting.
 */
@Component
@ConditionalOnProperty(name = "pulse.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String ADMIN_EMAIL = "admin@pulse.demo";
    private static final String OWNER_EMAIL = "owner@pulse.demo";
    private static final String LOGINS = ADMIN_EMAIL + " / demo-admin or " + OWNER_EMAIL + " / demo-owner";
    private static final String BASELINE = "v1.0.0";
    private static final String ROLLED_OUT = "v1.1.0";
    private static final String READY = "v1.2.0";
    private static final List<String> VERSIONS = List.of(BASELINE, ROLLED_OUT, READY);
    private static final String ROLLED_OUT_MODEL = "Pulse Vela";
    private static final List<Model> FLEET = List.of(
            new Model("Pulse Aurora", 'A', 12, 2024),
            new Model("Pulse Vela", 'V', 10, 2023),
            new Model("Pulse Terra", 'T', 8, 2025));
    private static final int OWNER_VEHICLES = 3;
    private static final Duration AGE = Duration.ofDays(7);

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final SoftwareVersionRepository versions;
    private final RolloutCampaignRepository campaigns;
    private final VehicleUpdateStatusRepository updates;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DemoDataSeeder(UserRepository users, VehicleRepository vehicles, SoftwareVersionRepository versions,
                          RolloutCampaignRepository campaigns, VehicleUpdateStatusRepository updates,
                          PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
                          PlatformTransactionManager transactionManager) {
        this.users = users;
        this.vehicles = vehicles;
        this.versions = versions;
        this.campaigns = campaigns;
        this.updates = updates;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (seed()) {
                log.info("Seeded the demo fleet; sign in as {}", LOGINS);
            }
        } catch (RuntimeException e) {
            log.error("Could not seed the demo data; the application continues without it", e);
        }
    }

    /**
     * Creates the demo data in one transaction. Returns false, changing nothing, if any of it is
     * already there (a restart, or a database that has some of the same users, VINs or versions).
     */
    public boolean seed() {
        Boolean seeded = tx.execute(status -> {
            if (users.existsByEmail(ADMIN_EMAIL)) {
                log.info("Demo data already present; sign in as {}", LOGINS);
                return false;
            }
            if (anyDemoRowExists()) {
                log.warn("Some demo users, vehicles or versions already exist but the demo admin doesn't; not seeding");
                return false;
            }
            Instant weekAgo = Instant.now().minus(AGE);
            User admin = user(ADMIN_EMAIL, "demo-admin", "Fleet Admin (demo)", Role.FLEET_ADMIN);
            User owner = user(OWNER_EMAIL, "demo-owner", "Olivia Owner (demo)", Role.OWNER);

            version(BASELINE, "Factory release.");
            SoftwareVersion rolledOut = version(ROLLED_OUT, "Faster charging curve and a calmer climate fan.");
            version(READY, "Remote start pre-conditions the battery; navigation map refresh.");

            List<Vehicle> rolledOutModel = new ArrayList<>();
            for (Model model : FLEET) {
                for (int i = 1; i <= model.count(); i++) {
                    boolean updated = model.name().equals(ROLLED_OUT_MODEL);
                    Vehicle vehicle = vehicles.save(Vehicle.builder()
                            .vin(vin(model, i))
                            .model(model.name())
                            .modelYear(model.year())
                            .owner(updated && i <= OWNER_VEHICLES ? owner : admin)
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

            UUID campaignId = completedRollout(rolledOut, rolledOutModel, admin, weekAgo.plus(Duration.ofHours(1)));
            vehicles.flush();
            backdateCreation(campaignId);
            return true;
        });
        return Boolean.TRUE.equals(seeded);
    }

    private boolean anyDemoRowExists() {
        return users.existsByEmail(OWNER_EMAIL)
                || VERSIONS.stream().anyMatch(versions::existsByVersionLabel)
                || FLEET.stream().anyMatch(model -> vehicles.existsByVin(vin(model, 1)));
    }

    private static String vin(Model model, int number) {
        return String.format("PLS%cDEMO%09d", model.code(), number);
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
     * splits a fleet (by VIN order), with every update installed. As in the app, the second stage
     * starts when the first one's last update finishes.
     */
    private UUID completedRollout(SoftwareVersion version, List<Vehicle> fleet, User admin, Instant start) {
        List<Vehicle> byVin = fleet.stream().sorted(Comparator.comparing(Vehicle::getVin)).toList();
        int firstStageSize = (int) Math.ceil(byVin.size() * 50 / 100.0);
        Duration perVehicle = Duration.ofMinutes(4);
        Duration install = Duration.ofMinutes(12);
        Instant firstStageDone = start.plus(perVehicle.multipliedBy(firstStageSize - 1)).plus(install);
        Instant secondStageDone = firstStageDone.plus(perVehicle.multipliedBy(byVin.size() - firstStageSize - 1)).plus(install);

        RolloutCampaign campaign = RolloutCampaign.builder()
                .softwareVersion(version)
                .targetModel(ROLLED_OUT_MODEL)
                .status(CampaignStatus.COMPLETED)
                .currentStageIndex(1)
                .failureThresholdPercent(10.0)
                .createdBy(admin)
                .build();
        campaign.setStages(new ArrayList<>(List.of(
                stage(campaign, 50, start, firstStageDone),
                stage(campaign, 100, firstStageDone, secondStageDone))));
        RolloutCampaign saved = campaigns.save(campaign);

        for (int i = 0; i < byVin.size(); i++) {
            boolean firstStage = i < firstStageSize;
            Instant updateStart = (firstStage ? start : firstStageDone)
                    .plus(perVehicle.multipliedBy(firstStage ? i : i - firstStageSize));
            updates.save(VehicleUpdateStatus.builder()
                    .vehicle(byVin.get(i))
                    .campaign(saved)
                    .targetVersion(version)
                    .stageIndex(firstStage ? 0 : 1)
                    .status(UpdateStatus.INSTALLED)
                    .progressPercent(100)
                    .startedAt(updateStart)
                    .completedAt(updateStart.plus(install))
                    .build());
        }
        return saved.getId();
    }

    private static RolloutStage stage(RolloutCampaign campaign, int percentage, Instant started, Instant completed) {
        return RolloutStage.builder()
                .campaign(campaign)
                .percentage(percentage)
                .status(RolloutStageStatus.COMPLETED)
                .startedAt(started)
                .completedAt(completed)
                .build();
    }

    /** Moves the seeded rows' creation times a week back, before the rollout history. */
    private void backdateCreation(UUID campaignId) {
        String interval = "created_at - interval '" + AGE.toDays() + " days'";
        jdbc.update("UPDATE users SET created_at = " + interval + " WHERE email IN (?, ?)", ADMIN_EMAIL, OWNER_EMAIL);
        jdbc.update("UPDATE software_versions SET created_at = " + interval + " WHERE version_label IN (?, ?, ?)",
                VERSIONS.toArray());
        jdbc.update("UPDATE vehicles SET created_at = " + interval + " WHERE vin LIKE 'PLS_DEMO%'");
        jdbc.update("UPDATE rollout_campaigns SET created_at = " + interval + " WHERE id = ?", campaignId);
    }

    private record Model(String name, char code, int count, int year) {
    }
}
