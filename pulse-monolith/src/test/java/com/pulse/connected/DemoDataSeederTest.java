package com.pulse.connected;

import com.pulse.connected.auth.Role;
import com.pulse.connected.auth.User;
import com.pulse.connected.auth.UserRepository;
import com.pulse.connected.demo.DemoDataSeeder;
import com.pulse.connected.domain.CampaignStatus;
import com.pulse.connected.ota.RolloutCampaign;
import com.pulse.connected.ota.RolloutCampaignRepository;
import com.pulse.connected.ota.RolloutCampaignService;
import com.pulse.connected.ota.SoftwareVersionRepository;
import com.pulse.connected.ota.VehicleUpdateStatusRepository;
import com.pulse.connected.ota.dto.CampaignResponse;
import com.pulse.connected.vehicle.Vehicle;
import com.pulse.connected.vehicle.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo seeder is off by default, and when run it creates a consistent demo fleet. It runs
 * inside a transaction that is rolled back, so the shared test database stays clean.
 */
class DemoDataSeederTest extends AbstractIntegrationTest {

    @Autowired private ApplicationContext context;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserRepository users;
    @Autowired private VehicleRepository vehicles;
    @Autowired private SoftwareVersionRepository versions;
    @Autowired private RolloutCampaignRepository campaigns;
    @Autowired private VehicleUpdateStatusRepository updates;
    @Autowired private RolloutCampaignService rolloutService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void isOffByDefault() {
        assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
    }

    @Test
    void seedsAConsistentDemoFleet_Once() {
        DemoDataSeeder seeder = new DemoDataSeeder(users, vehicles, versions, campaigns, updates, passwordEncoder, transactionManager);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long usersBefore = users.count();
            long vehiclesBefore = vehicles.count();

            seeder.seed();

            User admin = users.findByEmail("admin@pulse.demo").orElseThrow();
            User owner = users.findByEmail("owner@pulse.demo").orElseThrow();
            assertThat(admin.getRoles()).containsExactly(Role.FLEET_ADMIN);
            assertThat(owner.getRoles()).containsExactly(Role.OWNER);
            assertThat(passwordEncoder.matches("demo-admin", admin.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches("demo-owner", owner.getPasswordHash())).isTrue();

            List<Vehicle> demoFleet = vehicles.findAll().stream()
                    .filter(v -> v.getModel().startsWith("Pulse "))
                    .toList();
            assertThat(demoFleet).allSatisfy(v -> assertThat(v.getVin()).hasSize(17).startsWith("PLS"));
            assertThat(demoFleet.stream().collect(Collectors.groupingBy(Vehicle::getModel, Collectors.counting())))
                    .isEqualTo(Map.of("Pulse Aurora", 12L, "Pulse Vela", 10L, "Pulse Terra", 8L));
            assertThat(demoFleet).filteredOn(v -> v.getOwner().getId().equals(owner.getId()))
                    .hasSize(3)
                    .allSatisfy(v -> assertThat(v.getModel()).isEqualTo("Pulse Vela"));
            assertThat(demoFleet).allSatisfy(v -> assertThat(v.getCurrentSoftwareVersion())
                    .isEqualTo(v.getModel().equals("Pulse Vela") ? "v1.1.0" : "v1.0.0"));

            assertThat(versions.findByVersionLabel("v1.0.0")).isPresent();
            assertThat(versions.findByVersionLabel("v1.2.0")).isPresent();

            RolloutCampaign completed = campaigns.findAllByTargetModel("Pulse Vela").stream()
                    .filter(c -> c.getSoftwareVersion().getVersionLabel().equals("v1.1.0"))
                    .findFirst().orElseThrow();
            CampaignResponse response = rolloutService.mapToCampaignResponse(completed);
            assertThat(response.status()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(response.totalVehiclesTargeted()).isEqualTo(10);
            assertThat(response.vehiclesInstalled()).isEqualTo(10);
            assertThat(response.vehiclesFailed()).isZero();
            // Stage 0 is 50% of the model: the first 5 VINs, as the service would split it.
            assertThat(updates.findAllByCampaignId(completed.getId()))
                    .filteredOn(u -> u.getStageIndex() == 0)
                    .extracting(u -> u.getVehicle().getVin())
                    .containsExactlyInAnyOrderElementsOf(demoFleet.stream()
                            .filter(v -> v.getModel().equals("Pulse Vela"))
                            .map(Vehicle::getVin).sorted().limit(5).toList());

            // A restart finds the demo admin and changes nothing.
            seeder.seed();
            assertThat(users.count()).isEqualTo(usersBefore + 2);
            assertThat(vehicles.count()).isEqualTo(vehiclesBefore + 30);
        });

        assertThat(users.findByEmail("admin@pulse.demo")).isEmpty();
    }
}
